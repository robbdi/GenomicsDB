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

import org.genomicsdb.model.GenomicsDBExportConfiguration;
import org.genomicsdb.spark.Converter;
import org.genomicsdb.reader.GenomicsDBQuery; 

import org.apache.spark.sql.catalyst.InternalRow;
import java.util.Iterator;
import scala.collection.JavaConverters;
import org.json.simple.parser.ParseException;


public class GenomicsDBQueryToInternalRow extends Converter {

  private Iterator<InternalRow> iterator;
  private GenomicsDBInputPartition inputPartition;
  private GenomicsDBQuery query;
  private long queryHandle;

  private String workspace;
  private String vidMapping;
  private String callsetMapping;
  private String referenceGenome;
  private long segmentSize = 0L;
  private List<String> attributesList;


  public GenomicsDBQueryToInternalRow(GenomicsDBInputPartition iPartition){
    inputPartition = iPartition;
    GenomicsDBExportConfiguration.ExportConfiguration exportConfiguration;
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
    this.workspace = exportConfiguration.getWorkspace();
    this.vidMapping = exportConfiguration.getVidMappingFile();
    this.callsetMapping = exportConfiguration.getCallsetMappingFile();
    this.referenceGenome = exportConfiguration.getReferenceGenome();
    this.segmentSize = exportConfiguration.getSegmentSize().longValue();
    this.attributesList = exportConfiguration.getAttributesList();
    
    List<Interval> intervals;
   
    if (this.workspace != null && this.vidMapping != null && this.callsetMapping != null && this.referenceGenome != null){ 

      // connection
      if (this.segmentSize > 0) {
        this.queryHandle = query.connect(workspace, vidMappingFile, callsetMappingFile, referenceGenome, attributesList, segmentSize.longValue());
      } else {
        this.queryHandle = query.connect(workspace, vidMappingFile, callsetMappingFile, referenceGenome, attributesList);
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
    this.iterator = intervals.iterator();
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
  
  // this will be intervals at top level
  // and calls in a nested structure
  public InternalRow get(){
    Interval interval = this.iterator.next();
    List<VariantCall> vcs = interval.getCalls();
    // the schema is actually different here, at first attempt just make sure 
    // this is passed through with the GenomicsDBSchemaFactory
    ArrayList<Object> rowObjets = new ArrayList<>(inputPartition.getSchema().size());
    //rowObjects.add(UTF8String.fromString(    
    return InteralRow.fromSeq(JavaConverters.asScalaIteratorConverter(Iterator())()
  }

  public void close(){}

}
