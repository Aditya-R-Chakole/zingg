package zingg.spark.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.types.DataType;
import zingg.util.BlockingTreeUtil;

public class SparkBlockingTreeUtil extends BlockingTreeUtil<Dataset<Row>, Row, Column, DataType, DataType>{

    public static final Log LOG = LogFactory.getLog(SparkBlockingTreeUtil.class);
	

    }
