/*
 * The MIT License (MIT)
 * Copyright (c) 2018 Omics Data Automation
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

import htsjdk.tribble.CloseableTribbleIterator;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import org.apache.spark.sql.catalyst.InternalRow;
import org.apache.spark.sql.catalyst.util.ArrayData;
import org.apache.spark.sql.connector.read.PartitionReader;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.unsafe.types.UTF8String;
import org.genomicsdb.reader.GenomicsDBFeatureReader;
import org.genomicsdb.model.Coordinates;
import org.genomicsdb.model.GenomicsDBExportConfiguration;
import org.apache.spark.sql.types.StructType;

import org.genomicsdb.spark.GenomicsDBInput;
import org.genomicsdb.spark.GenomicsDBVidSchema;
import org.genomicsdb.spark.Converter; 
import org.genomicsdb.spark.GenomicsDBConfiguration;
import java.util.Iterator;


import org.json.simple.parser.ParseException;
import scala.collection.JavaConverters;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Used by an executor to read data from storage, as an internal row based on the schema. 
 **/
public class GenomicsDBInputPartitionReader implements PartitionReader<InternalRow> {

  private Converter converter;
  private Iterator<InternalRow> iterator;
  private String readerType;

  public GenomicsDBInputPartitionReader(GenomicsDBInputPartition inputPartition) { //throws RuntimeException {
    readerType = inputPartition.getReaderType();
    System.out.println("readerType "+readerType);
    if (readerType == GenomicsDBConfiguration.DEFAULT_READER){  
      converter = new VariantContextToInternalRow(inputPartition);
    }else{// if (readerType == GenomicsDBConfiguration.GDBQUERY_READER){
      converter = new GenomicsDBQueryToInternalRow(inputPartition);
    }
    //else{
    //  throw new RuntimeException("Unsupported reader type "+readerType);
    //}
    iterator = converter.getIterator();
  }

  public InternalRow get() {
    return this.converter.get(); 
  }

  public boolean next() {
    return this.iterator.hasNext();
  }

  public void close() {
    if (readerType == "VariantContext"){
      this.converter.close();
    }
  }

}
