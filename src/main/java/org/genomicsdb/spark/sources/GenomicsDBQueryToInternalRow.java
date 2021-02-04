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
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import scala.collection.JavaConverters;
import org.json.simple.parser.ParseException;
import java.io.IOException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.stream.Stream;

public class GenomicsDBQueryToInternalRow extends Converter {

  private Iterator<VariantCall> iterator;
  private GenomicsDBInputPartition inputPartition;
  private GenomicsDBExportConfiguration.ExportConfiguration exportConfiguration;
  private GenomicsDBQuery query;
  private long queryHandle;

  private String workspace;
  private String vidMapping;
  private String callsetMapping;
  private String referenceGenome;
  private long segmentSize = 0L;
  private List<String> attributesList;


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
    setAttributes(loader);
    this.attributesList = exportConfiguration.getAttributesList();
    
    List<Interval> intervals;
   
    if (this.workspace != null && this.vidMapping != null && this.callsetMapping != null && this.referenceGenome != null){ 

      // connection
      if (this.segmentSize > 0) {
        this.queryHandle = query.connect(workspace, vidMapping, callsetMapping, referenceGenome, attributesList, segmentSize);
      } else {
        this.queryHandle = query.connect(workspace, vidMapping, callsetMapping, referenceGenome, attributesList);
      }

      // query
      intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName(),
                  ToColumnRangePairs(exportConfiguration.getQueryColumnRanges(0).getColumnOrIntervalListList()),
                  ToRowRangePairs(exportConfiguration.getQueryRowRanges(0).getRangeListList()));
    } else {
      
      queryHandle = query.connectExportConfiguration(exportConfiguration);
      intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName());
    
    }
    query.disconnect(queryHandle);
    this.iterator = intervals.stream().flatMap(i -> i.getCalls().stream()).iterator();
  }


  private boolean check_configuration(String key, String value) {
    if (value == null || value.isEmpty()) {
      System.err.println("GenomicsDB Configuration does not contain value for key=" + key);
      return false;
    } else {
      return true;
    }
  }

  private void setAttributes(String loader) throws RuntimeException {
    JSONObject jsonObject = null;
    try {
      JSONParser parser = new JSONParser();
      jsonObject = (JSONObject)parser.parse(new FileReader(loader));
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (ParseException e) {
      e.printStackTrace();
    }
    if (jsonObject != null) {
      this.workspace = (this.exportConfiguration.hasWorkspace())?this.exportConfiguration.getWorkspace():(String)jsonObject.get("workspace");
      this.vidMapping = (this.exportConfiguration.hasVidMappingFile())?this.exportConfiguration.getVidMappingFile():(String)jsonObject.get("vid_mapping_file");
      this.callsetMapping = (this.exportConfiguration.hasCallsetMappingFile())?this.exportConfiguration.getCallsetMappingFile():(String)jsonObject.get("callset_mapping_file");
      this.referenceGenome = (this.exportConfiguration.hasReferenceGenome())?this.exportConfiguration.getReferenceGenome():(String)jsonObject.get("reference_genome");
      this.segmentSize = (this.exportConfiguration.hasSegmentSize())?this.exportConfiguration.getSegmentSize():(Long)jsonObject.get("segment_size");
      if (!check_configuration("workspace", this.workspace) ||
          !check_configuration("vid_mapping_file", this.vidMapping) ||
          !check_configuration("callset_mapping_file", this.callsetMapping) ||
          !check_configuration("reference_genome", this.referenceGenome)) {
          throw new RuntimeException("GenomicsDBConfiguration is incomplete. Add required configuration values and restart the operation");
      }
    }
  }

  // this is dupliated functionality from the api class, should consider moving to a util class 
  // to avoid duplication.
  private List<Pair> ToColumnRangePairs(List<Coordinates.GenomicsDBColumnOrInterval> intervalLists) {
    List<Pair> intervalPairs = new ArrayList<>();
    for (Coordinates.GenomicsDBColumnOrInterval interval : intervalLists) {
      assert (interval.getColumnInterval().hasTiledbColumnInterval());
      Coordinates.TileDBColumnInterval tileDBColumnInterval =
        interval.getColumnInterval().getTiledbColumnInterval();
      interval.getColumnInterval().getTiledbColumnInterval();
      assert (tileDBColumnInterval.hasBegin() && tileDBColumnInterval.hasEnd());
      intervalPairs.add(
        new Pair(tileDBColumnInterval.getBegin(), tileDBColumnInterval.getEnd()));
    }
    return intervalPairs;
  }


  private List<Pair> ToRowRangePairs(List<GenomicsDBExportConfiguration.RowRange> rowRanges) {
    List<Pair> rangePairs = new ArrayList<>();
    for (GenomicsDBExportConfiguration.RowRange range : rowRanges) {
      assert (range.hasLow() && range.hasLow());
      rangePairs.add(new Pair(range.getLow(), range.getHigh()));
    }
    return rangePairs;
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
    callObjects.add(UTF8String.fromString(vc.getSampleName()));
    callObjects.add(vc.getGenomic_interval().getStart());
    callObjects.add(vc.getGenomic_interval().getEnd());
    
    InternalRow iRow =
      InternalRow.fromSeq(
        JavaConverters.asScalaIteratorConverter(callObjects.iterator()).asScala().toSeq());
    return iRow;
  }

  public void close(){}

}
