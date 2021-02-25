/*
 * The MIT License (MIT)
 * Copyright (c) 2016-2017 Intel Corporation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.genomicsdb.spark.sources;

import org.genomicsdb.GenomicsDBQueryUtils;
import org.genomicsdb.spark.GenomicsDBInput;
import org.genomicsdb.model.GenomicsDBExportConfiguration;
import org.genomicsdb.spark.Converter;
import org.genomicsdb.reader.GenomicsDBQuery; 
import org.genomicsdb.reader.GenomicsDBQuery.Interval;
import org.genomicsdb.reader.GenomicsDBQuery.Pair;
import org.genomicsdb.reader.GenomicsDBQuery.VariantCall;
import org.genomicsdb.model.Coordinates;

import org.apache.spark.unsafe.types.UTF8String;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.catalyst.util.GenericArrayData;
import org.apache.spark.sql.catalyst.InternalRow;

import scala.collection.JavaConverters;

import org.json.simple.parser.ParseException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.FileReader;

import java.lang.StringIndexOutOfBoundsException;
import java.util.stream.Stream;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

public class GenomicsDBQueryToInternalRow extends Converter {

  private Iterator<VariantCall> iterator;
  private GenomicsDBInputPartition inputPartition;
  private GenomicsDBExportConfiguration.ExportConfiguration exportConfiguration;
  private GenomicsDBQuery query;
  private long queryHandle;

  public GenomicsDBQueryToInternalRow(GenomicsDBInputPartition iPartition) {
    inputPartition = iPartition;
    String loader = iPartition.getLoader();
    try {
      exportConfiguration =
        GenomicsDBInput.createTargetExportConfigurationPB(
          inputPartition.getQuery(), inputPartition.getPartitionInfo(),
          inputPartition.getQueryInfoList(), inputPartition.getQueryIsPB());
    } catch (ParseException | IOException e) {
      e.printStackTrace();
      exportConfiguration = null;
    }
    
    this.query = new GenomicsDBQuery();

    // connect parameters
    JSONObject jsonObject = null;
    jsonObject = GenomicsDBQueryUtils.loadJson(loader);

    String workspace = GenomicsDBQueryUtils.getWorkspace(exportConfiguration, jsonObject);
    String vidMapping = GenomicsDBQueryUtils.getVidMapping(exportConfiguration, jsonObject);
    String callsetMapping = GenomicsDBQueryUtils.getCallsetMapping(exportConfiguration, jsonObject);
    String referenceGenome = GenomicsDBQueryUtils.getReferenceGenome(exportConfiguration, jsonObject);
    long segmentSize = GenomicsDBQueryUtils.getSegmentSize(exportConfiguration, jsonObject);
    List<String> attributesList = exportConfiguration.getAttributesList();
    
    List<Interval> intervals;
   
    if (jsonObject != null){
      // connection
      if (segmentSize > 0) {
        queryHandle = query.connect(workspace, vidMapping, callsetMapping, referenceGenome, attributesList, segmentSize);
      } else {
        queryHandle = query.connect(workspace, vidMapping, callsetMapping, referenceGenome, attributesList);
      }

      // query
      try{
        intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName(),
                GenomicsDBQueryUtils.ToColumnRangePairs(exportConfiguration.getQueryColumnRanges(0).getColumnOrIntervalListList()),
                GenomicsDBQueryUtils.ToRowRangePairs(exportConfiguration.getQueryRowRanges(0).getRangeListList()));
      }catch (IndexOutOfBoundsException e){
        intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName(),
                GenomicsDBQueryUtils.ToColumnRangePairs(exportConfiguration.getQueryColumnRanges(0).getColumnOrIntervalListList()));
      }
  } else {
    
    queryHandle = query.connectExportConfiguration(exportConfiguration);
    intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName());
  
  }
  query.disconnect(queryHandle);
  this.iterator = intervals.stream().flatMap(i -> i.getCalls().stream()).iterator();
}

public Iterator getIterator(){
  return this.iterator;
}

public InternalRow get(){
  VariantCall vc = this.iterator.next();
  // the schema is actually different here, at first attempt just make sure 
  // this is passed through with the GenomicsDBSchemaFactory
  ArrayList<Object> callObjects = new ArrayList<>(inputPartition.getSchema().size());
  callObjects.add(vc.getRowIndex());
  callObjects.add(vc.getColIndex());
  callObjects.add(UTF8String.fromString(vc.getSampleName()));
  callObjects.add(UTF8String.fromString(vc.getContigName()));
  callObjects.add(vc.getGenomic_interval().getStart());
  callObjects.add(vc.getGenomic_interval().getEnd());
  
  // go through the genomic fields, and extract common fields
  Map<String, Object> gfields = vc.getGenomicFields();
  if (gfields.containsKey("REF")){
    String ref = (String)gfields.remove("REF");
    callObjects.add(UTF8String.fromString(ref));
  }else{
    callObjects.add(null);
  }
  // better representation in VariantCall structure?
  if (gfields.containsKey("ALT")){
    String[] alt = (String[])gfields.remove("ALT");
    UTF8String[] alts = new UTF8String[alt.length];
    int i = 0;
    for (String a: alt){
      alts[i++] = UTF8String.fromString(a);
    }
    callObjects.add(ArrayData.toArrayData(alts));
  }else{
    callObjects.add(null);
  } 
  if (gfields.containsKey("GT")){
    int[] genotype = (int[])gfields.remove("GT");
    callObjects.add(ArrayData.toArrayData(genotype));
  }else{
    callObjects.add(null);
  }

  // remaining attributes
  callObjects.add(UTF8String.fromString(gfields.toString()));

  InternalRow iRow =
    InternalRow.fromSeq(
      JavaConverters.asScalaIteratorConverter(callObjects.iterator()).asScala().toSeq());
  return iRow;
}

public void close(){}

}
