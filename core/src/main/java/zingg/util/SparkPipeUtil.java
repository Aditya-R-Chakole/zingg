package zingg.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.DataFrameReader;
import org.apache.spark.sql.DataFrameWriter;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SaveMode;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.storage.StorageLevel;

//import zingg.scala.DFUtil;
import zingg.client.Arguments;
import zingg.client.util.ColName;
import zingg.client.pipe.CassandraPipe;
import zingg.client.pipe.ElasticPipe;
import zingg.client.pipe.FilePipe;
import zingg.client.pipe.Format;
import zingg.client.pipe.Pipe;
import scala.Option;
import scala.collection.JavaConverters;
import scala.collection.Seq;

//import com.datastax.driver.core.ProtocolVersion;
//import com.datastax.driver.core.ResultSet;
//import com.datastax.driver.core.Session;
//import com.datastax.spark.connector.DataFrameFunctions;
import com.datastax.spark.connector.cql.CassandraConnector;
import com.datastax.spark.connector.cql.ClusteringColumn;
import com.datastax.spark.connector.cql.ColumnDef;
import com.datastax.spark.connector.cql.TableDef;

import com.datastax.spark.connector.cql.*;
import zingg.scala.DFUtil;

//import com.datastax.spark.connector.cql.*;
//import org.elasticsearch.spark.sql.api.java.JavaEsSparkSQL;
//import zingg.scala.DFUtil;

public class SparkPipeUtil implements PipeUtilBase<Dataset<Row>, SparkSession>{

	public  final Log LOG = LogFactory.getLog(PipeUtil.class);

	private DataFrameReader getReader(SparkSession spark, Pipe p) {
		DataFrameReader reader = spark.read();

		LOG.warn("Reading input " + p.getFormat().type());
		reader = reader.format(p.getFormat().type());
		if (p.getSchema() != null) {
			reader = reader.schema(p.getSchema());
		}
		for (String key : p.getProps().keySet()) {
			reader = reader.option(key, p.get(key));
		}
		reader = reader.option("mode", "PERMISSIVE");
		return reader;
	}

	private  Dataset<Row> read(DataFrameReader reader, Pipe p, boolean addSource) {
		Dataset<Row> input = null;
		LOG.warn("Reading " + p);
		if (p.getProps().containsKey(FilePipe.LOCATION)) {
			input = reader.load(p.get(FilePipe.LOCATION));
		}
		else {
			input = reader.load();
		}
		if (addSource) {
			input = input.withColumn(ColName.SOURCE_COL, functions.lit(p.getName()));			
		}
		return input;
	}

	public  Dataset<Row> readInternal(SparkSession spark, Pipe p, boolean addSource) {
		DataFrameReader reader = getReader(spark, p);
		return read(reader, p, addSource);		
	}

	
	public  Dataset<Row> readInternal(SparkSession spark, boolean addLineNo,
			boolean addSource, Pipe... pipes) {
		Dataset<Row> input = null;

		for (Pipe p : pipes) {
			if (input == null) {
				input = readInternal(spark, p, addSource);
				LOG.debug("input size is " + input.count());				
			} else {
					input = input.union(readInternal(spark, p, addSource));
			}
		}
		// we will probably need to create row number as string with pipename/id as
		// suffix
		if (addLineNo)
			input = DFUtil.addRowNumber(input, spark);
		// we need to transform the input here by using stop words
		return input;
	}

	public  Dataset<Row> read(SparkSession spark, boolean addLineNo, boolean addSource, Pipe... pipes) {
		Dataset<Row> rows = readInternal(spark, addLineNo, addSource, pipes);
		rows = rows.persist(StorageLevel.MEMORY_ONLY());
		return rows;
	}

	public  Dataset<Row> sample(SparkSession spark, Pipe p) {
		DataFrameReader reader = getReader(spark, p);
		reader.option("inferSchema", true);
		reader.option("mode", "DROPMALFORMED");
		LOG.info("reader is ready to sample with inferring " + p.get(FilePipe.LOCATION));
		LOG.warn("Reading input of type " + p.getFormat().type());
		Dataset<Row> input = read(reader, p, false);
		// LOG.warn("inferred schema " + input.schema());
		List<Row> values = input.takeAsList(10);
		values.forEach(r -> LOG.warn(r));
		Dataset<Row> ret = spark.createDataFrame(values, input.schema());
		return ret;

	}

	public Dataset<Row> read(SparkSession spark, boolean addLineNo, int numPartitions,
			boolean addSource, Pipe... pipes) {
		Dataset<Row> rows = readInternal(spark, addLineNo, addSource, pipes);
		rows = rows.repartition(numPartitions);
		rows = rows.persist(StorageLevel.MEMORY_ONLY());
		return rows;
	}

	public  void write(Dataset<Row> toWriteOrig, Arguments args, Pipe... pipes) {
			for (Pipe p: pipes) {
			Dataset<Row> toWrite = toWriteOrig;
			DataFrameWriter writer = toWrite.write();
		
			LOG.warn("Writing output " + p);
			
			if (p.getMode() != null) {
				writer.mode(p.getMode());
			}
			else {
				writer.mode("Append");
			}			
			writer = writer.format(p.getFormat().type());
			
			for (String key: p.getProps().keySet()) {
				writer = writer.option(key, p.get(key));
			}
			if (p.getFormat() == Format.CASSANDRA) {
				/*
				ctx.getConf().set(CassandraPipe.HOST, p.getProps().get(CassandraPipe.HOST));
				toWrite.sparkSession().conf().set(CassandraPipe.HOST, p.getProps().get(CassandraPipe.HOST));
				//df.createCassandraTable(p.get("keyspace"), p.get("table"), opPk, opCl, CassandraConnector.apply(ctx.getConf()));
				
				CassandraConnector connector = CassandraConnector.apply(ctx.getConf());
				try (Session session = connector.openSession()) {
					ResultSet rs = session.execute("SELECT table_name FROM system_schema.tables WHERE keyspace_name='" 
								+ p.get(CassandraPipe.KEYSPACE) + "' AND table_name='" + p.get(CassandraPipe.TABLE) + "'");
					if (rs.all().size() == 0) {
						List<String> pk =  new ArrayList<String>();
						if (p.get(CassandraPipe.PRIMARY_KEY) != null) {
							//pk.add(p.get(CassandraPipe.PRIMARY_KEY));
							pk = Arrays.asList(p.get(CassandraPipe.PRIMARY_KEY).split(","));
						}
						Option<Seq<String>> opPk = Option.apply(JavaConverters.asScalaIteratorConverter(pk.iterator()).asScala().toSeq());
						List<String> cl =  new ArrayList<String>();
						
						if (p.getAddProps()!= null && p.getAddProps().containsKey("clusterBy")) {
							cl=Arrays.asList(p.getAddProps().get("clusterBy").split(","));
						}
						Option<Seq<String>> opCl = Option.apply(JavaConverters.asScalaIteratorConverter(cl.iterator()).asScala().toSeq());
						
						DataFrameFunctions df = new DataFrameFunctions(toWrite); 
						LOG.warn("received cassandra table  - " + p.get(CassandraPipe.KEYSPACE) + " and " + p.get(CassandraPipe.TABLE));
						df.createCassandraTable(p.get(CassandraPipe.KEYSPACE), p.get(CassandraPipe.TABLE), opPk, opCl, CassandraConnector.apply(ctx.getConf()));
						if (p.getAddProps()!= null && p.getAddProps().containsKey("indexBy")) {
							LOG.warn("creating index on cassandra");

							session.execute("CREATE INDEX " +  p.getAddProps().get("indexBy") + p.get(CassandraPipe.KEYSPACE) + "_" +  
									p.get(CassandraPipe.TABLE) + "_idx ON " + p.get(CassandraPipe.KEYSPACE) + "." +  
									p.get(CassandraPipe.TABLE) + "(" + p.getAddProps().get("indexBy") + 
									")");
						}
					}
					else {
						LOG.warn("existing cassandra table  - " + p.get(CassandraPipe.KEYSPACE) + " and " + p.get(CassandraPipe.TABLE));
					
					}
					
				}
				catch(Exception e) {
					e.printStackTrace();
					LOG.warn("Writing issue");
				}*/
			}
			else if (p.getProps().containsKey("location")) {
				LOG.warn("Writing file");
				writer.save(p.get(FilePipe.LOCATION));
			}	
			else if (p.getFormat().equals(Format.JDBC)){
				writer = toWrite.write();
				writer = writer.format(p.getFormat().type());				

				if (p.getMode() != null) {
					writer.mode(p.getMode());
				}
				else {
					writer.mode("Append");
				}
				for (String key: p.getProps().keySet()) {
					writer = writer.option(key, p.get(key));
				}
				writer.save();
			}
			else {			
				writer.save();
			
			}
			
			
		}

	}

	public  void writePerSource(Dataset<Row> toWrite, Arguments args, Pipe[] pipes ) {
		List<Row> sources = toWrite.select(ColName.SOURCE_COL).distinct().collectAsList();
		for (Row r : sources) {
			Dataset<Row> toWriteNow = toWrite.filter(toWrite.col(ColName.SOURCE_COL).equalTo(r.get(0)));
			toWriteNow = toWriteNow.drop(ColName.SOURCE_COL);
			write(toWriteNow, args, pipes);
		}
	}

	public  Pipe getTrainingDataUnmarkedPipe(Arguments args) {
		Pipe p = new Pipe();
		p.setFormat(Format.PARQUET);
		p.setProp(FilePipe.LOCATION, args.getZinggTrainingDataUnmarkedDir());
		return p;
	}

	public  Pipe getTrainingDataMarkedPipe(Arguments args) {
		Pipe p = new Pipe();
		p.setFormat(Format.PARQUET);
		p.setProp(FilePipe.LOCATION, args.getZinggTrainingDataMarkedDir());
		return p;
	}
	
	public  Pipe getModelDocumentationPipe(Arguments args) {
		Pipe p = new Pipe();
		p.setFormat(Format.TEXT);
		p.setProp(FilePipe.LOCATION, args.getZinggDocFile());
		return p;
	}
	
	public  Pipe getBlockingTreePipe(Arguments args) {
		Pipe p = new Pipe();
		p.setFormat(Format.PARQUET);
		p.setProp(FilePipe.LOCATION, args.getBlockFile());
		p.setMode(SaveMode.Overwrite);
		return p;
	}

	public  String getPipesAsString(Pipe[] pipes) {
		return Arrays.stream(pipes)
			.map(p -> p.getFormat().type())
			.collect(Collectors.toList())
			.stream().reduce((p1, p2) -> p1 + "," + p2)
			.map(Object::toString)
			.orElse("");
	}

	/*
	 * public  String getTableCreateCQL(Pipe p, Dataset<Row> df) {
	 * 
	 * Set<String> partitionKeys = new TreeSet<String>() { {
	 * add(p.get(CassandraPipe.PRIMARY_KEY)); } }; int c = 0; Map<String, Integer>
	 * clustereingKeys = new TreeMap<String, Integer>(); if (p.getAddProps()!= null
	 * && p.getAddProps().containsKey("clusterBy")) { for (String clBy :
	 * p.getAddProps().get("clusterBy").split(",")) { { clustereingKeys.put(clBy,
	 * c++); } } }
	 * 
	 * TableDef td = TableDef.fromDataFrame(df, p.get(CassandraPipe.KEYSPACE),
	 * p.get(CassandraPipe.TABLE), ProtocolVersion.NEWEST_SUPPORTED);
	 * 
	 * List<ColumnDef> partKeyList = new ArrayList<ColumnDef>(); List<ColumnDef>
	 * clusterColumnList = new ArrayList<ColumnDef>(); List<ColumnDef>
	 * regColulmnList = new ArrayList<ColumnDef>();
	 * 
	 * scala.collection.Iterator<ColumnDef> iter = td.allColumns() .iterator();
	 * while (iter.hasNext()) { ColumnDef col = iter.next(); String colName =
	 * col.columnName(); if (partitionKeys.contains(colName)) { partKeyList.add(new
	 * ColumnDef(colName, PartitionKeyColumn$.MODULE$, col.columnType())); } else if
	 * (clustereingKeys.containsKey(colName)) { int idx =
	 * clustereingKeys.get(colName); clusterColumnList.add(new ColumnDef(colName,
	 * new ClusteringColumn(idx), col.columnType())); } else { if
	 * (colName.equals("dob")) { LOG.warn("yes dob"); regColulmnList.add(new
	 * ColumnDef(colName, new StaticColumn$.MODULE$, col.columnType())); } else {
	 * regColulmnList.add(new ColumnDef(colName, RegularColumn$.MODULE$,
	 * col.columnType())); } } }
	 * 
	 * TableDef newTd = new TableDef(td.keyspaceName(), td.tableName(),
	 * JavaConverters.asScalaIteratorConverter(partKeyList.iterator()).asScala().
	 * toSeq(),
	 * JavaConverters.asScalaIteratorConverter(clusterColumnList.iterator()).asScala
	 * ().toSeq() ,
	 * JavaConverters.asScalaIteratorConverter(regColulmnList.iterator()).asScala().
	 * toSeq(), td.indexes(), td.isView()); String cql = newTd.cql();
	 * System.out.println(cql); System.out.println(cql.replace(",dob", newChar));
	 * 
	 * return cql;
	 * 
	 * }
	 */

}
