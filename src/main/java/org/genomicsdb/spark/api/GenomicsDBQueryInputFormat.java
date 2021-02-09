package org.genomicsdb.spark.api;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.*;
import org.genomicsdb.model.Coordinates;
import org.genomicsdb.model.GenomicsDBExportConfiguration;
import org.genomicsdb.reader.GenomicsDBQuery;
import org.genomicsdb.reader.GenomicsDBQuery.Interval;
import org.genomicsdb.reader.GenomicsDBQuery.Pair;
import org.genomicsdb.reader.GenomicsDBQuery.VariantCall;
import org.genomicsdb.spark.GenomicsDBConfiguration;
import org.genomicsdb.spark.GenomicsDBInput;
import org.genomicsdb.spark.GenomicsDBInputSplit;
import org.genomicsdb.spark.GenomicsDBQueryUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GenomicsDBQueryInputFormat extends InputFormat<Interval, List<VariantCall>> implements Configurable {

  private Configuration configuration;
  private GenomicsDBInput<GenomicsDBInputSplit> input;

  @Override
  public List<InputSplit> getSplits(JobContext jobContext) throws IOException, InterruptedException {
    GenomicsDBConfiguration genomicsDBConfiguration = new GenomicsDBConfiguration(configuration);

    // At least a query in the form of json or protobuf should be passed
    if (configuration.get(GenomicsDBConfiguration.QUERYPB) == null && configuration.get(GenomicsDBConfiguration.QUERYJSON) == null) {
      throw new IOException("Query json or query protobuf has to be specified.");
    }

    if (configuration.get(GenomicsDBConfiguration.LOADERJSON) != null) {
      genomicsDBConfiguration.setLoaderJsonFile(
          configuration.get(GenomicsDBConfiguration.LOADERJSON));
    }
    if (configuration.get(GenomicsDBConfiguration.QUERYPB) != null) {
      genomicsDBConfiguration.setQueryPB(
              configuration.get(GenomicsDBConfiguration.QUERYPB));
    } else if (configuration.get(GenomicsDBConfiguration.QUERYJSON) != null){
      genomicsDBConfiguration.setQueryJsonFile(
              configuration.get(GenomicsDBConfiguration.QUERYJSON));
    }
    if (configuration.get(GenomicsDBConfiguration.MPIHOSTFILE) != null) {
      genomicsDBConfiguration.setHostFile(
              configuration.get(GenomicsDBConfiguration.MPIHOSTFILE));
    }
    setConf(genomicsDBConfiguration);
    input.setGenomicsDBConfiguration(genomicsDBConfiguration);
    return (List)input.divideInput();
  }

  @Override
  public RecordReader<Interval, List<VariantCall>> createRecordReader(
      InputSplit inputSplit, TaskAttemptContext taskAttemptContext)
      throws IOException, InterruptedException {
    String loaderJson;
    String queryJson;

    GenomicsDBInputSplit gSplit = (GenomicsDBInputSplit)inputSplit;

    boolean isPB;
    if (taskAttemptContext != null) {
      configuration = taskAttemptContext.getConfiguration();
    } else {
      assert(configuration!=null);
    }

    loaderJson = configuration.get(GenomicsDBConfiguration.LOADERJSON);
    if (configuration.get(GenomicsDBConfiguration.QUERYPB) != null) {
      queryJson = configuration.get(GenomicsDBConfiguration.QUERYPB);
      isPB = true;
    } else {
      queryJson = configuration.get(GenomicsDBConfiguration.QUERYJSON);
      isPB = false;
    }

    // Need to amend query file being passed in based on inputSplit
    // so we'll create an appropriate protobuf object
    GenomicsDBExportConfiguration.ExportConfiguration exportConfiguration;
    try {
      exportConfiguration =
              GenomicsDBInput.createTargetExportConfigurationPB(queryJson,
                      gSplit.getPartitionInfo(),
                      gSplit.getQueryInfoList(), isPB);
    }
    catch (ParseException e) {
      e.printStackTrace();
      return null;
    }

    return new RecordReader<Interval, List<VariantCall>>() {
      boolean initialized = false;
      List<Interval> intervals;
      Iterator<Interval> intervalIterator;
      Interval currentInterval;
      int numProcessedIntervals = 0;

      GenomicsDBQuery query = new GenomicsDBQuery();

      private boolean check_configuration(String key, String value) {
        if (value == null || value.isEmpty()) {
          System.err.println("GenomicsDB Configuration does not contain value for key=" + key);
          return false;
        } else {
          return true;
        }
      }

      @Override
      public void initialize(InputSplit inputSplit, TaskAttemptContext taskAttemptContext)
              throws IOException, InterruptedException {

        JSONObject jsonObject = null;
        jsonObject = GenomicsDBQueryUtils.loadJson(configuration.get(GenomicsDBConfiguration.LOADERJSON));

        assert (exportConfiguration.hasArrayName());

        long queryHandle;
        if (jsonObject != null) {
          String workspace = GenomicsDBQueryUtils.getWorkspace(exportConfiguration, jsonObject);
          String vidMappingFile = GenomicsDBQueryUtils.getVidMapping(exportConfiguration, jsonObject);
          String callsetMappingFile = GenomicsDBQueryUtils.getCallsetMapping(exportConfiguration, jsonObject);
          String referenceGenome = GenomicsDBQueryUtils.getReferenceGenome(exportConfiguration, jsonObject);
          Long segmentSize = GenomicsDBQueryUtils.getSegmentSize(exportConfiguration, jsonObject);
          List<String> attributesList = exportConfiguration.getAttributesList();

          if (segmentSize > 0) {
            queryHandle = query.connect(workspace, vidMappingFile, callsetMappingFile, referenceGenome, attributesList, segmentSize.longValue());
          } else {
            queryHandle = query.connect(workspace, vidMappingFile, callsetMappingFile, referenceGenome, attributesList);
          }
          intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName(),
                  GenomicsDBQueryUtils.ToColumnRangePairs(exportConfiguration.getQueryColumnRanges(0).getColumnOrIntervalListList()),
                  GenomicsDBQueryUtils.ToRowRangePairs(exportConfiguration.getQueryRowRanges(0).getRangeListList()));
        } else {
          queryHandle = query.connectExportConfiguration(exportConfiguration);
          intervals = query.queryVariantCalls(queryHandle, exportConfiguration.getArrayName());
        }
        query.disconnect(queryHandle);

        intervalIterator = intervals.iterator();
        initialized = true;
      }

      @Override
      public boolean nextKeyValue() throws IOException, InterruptedException {
        if (initialized && intervalIterator.hasNext()) {
          currentInterval = intervalIterator.next();
          numProcessedIntervals++;
          return true;
        } else {
          currentInterval = null;
          return false;
        }
      }

      @Override
      public Interval getCurrentKey() throws IOException, InterruptedException {
        return currentInterval;
      }

      @Override
      public List<VariantCall> getCurrentValue() throws IOException, InterruptedException {
        return getCurrentKey().getCalls();
      }

      @Override
      public float getProgress() throws IOException, InterruptedException {
        return numProcessedIntervals / intervals.size();
      }

      @Override
      public void close() throws IOException {}
    };
  }

  /** default constructor */
  public GenomicsDBQueryInputFormat() {
    input = new GenomicsDBInput<>(null, null, null, 1, Long.MAX_VALUE, GenomicsDBInputSplit.class);
  }

  public GenomicsDBQueryInputFormat(GenomicsDBConfiguration conf) {
    this.configuration = conf;
    input = new GenomicsDBInput<>(conf, null, null, 1, Long.MAX_VALUE, GenomicsDBInputSplit.class);
  }

  @Override
  public void setConf(Configuration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Configuration getConf() {
    return configuration;
  }
}
