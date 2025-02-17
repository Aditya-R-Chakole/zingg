import zingg.client._;
import java.util.ArrayList;

//setting silent mode for Argument creation only 
:silent

//build the arguments for zingg
val args = new Arguments();
//set field definitions
val fname = new FieldDefinition();
fname.setFieldName("fname");
fname.setDataType("\"string\"");
fname.setMatchType(MatchType.FUZZY);
fname.setFields("fname");

val lname = new FieldDefinition();
lname.setFieldName("lname");
lname.setDataType("\"string\"");
lname.setMatchType(MatchType.FUZZY);
lname.setFields("lname");

val stNo = new FieldDefinition();
stNo.setFieldName("stNo");
stNo.setDataType("\"string\"");
stNo.setMatchType(MatchType.FUZZY);
stNo.setFields("stNo");

val add1 = new FieldDefinition();
add1.setFieldName("add1");
add1.setDataType("\"string\"");
add1.setMatchType(MatchType.FUZZY);
add1.setFields("add1");

val add2 = new FieldDefinition();
add2.setFieldName("add2");
add2.setDataType("\"string\"");
add2.setMatchType(MatchType.FUZZY);
add2.setFields("add2");

val city = new FieldDefinition();
city.setFieldName("city");
city.setDataType("\"string\"");
city.setMatchType(MatchType.FUZZY);
city.setFields("city");

val areacode = new FieldDefinition();
areacode.setFieldName("areacode");
areacode.setDataType("\"string\"");
areacode.setMatchType(MatchType.FUZZY);
areacode.setFields("areacode");

val state = new FieldDefinition();
state.setFieldName("state");
state.setDataType("\"string\"");
state.setMatchType(MatchType.FUZZY);
state.setFields("state");

val dob = new FieldDefinition();
dob.setFieldName("dob");
dob.setDataType("\"string\"");
dob.setMatchType(MatchType.FUZZY);
dob.setFields("dob");

val ssn = new FieldDefinition();
ssn.setFieldName("ssn");
ssn.setDataType("\"string\"");
ssn.setMatchType(MatchType.FUZZY);
ssn.setFields("ssn");
:silent

val fieldDef = new ArrayList[FieldDefinition]();
fieldDef.add(fname);
fieldDef.add(lname);
fieldDef.add(stNo);
fieldDef.add(add1);
fieldDef.add(add2);
fieldDef.add(city);
fieldDef.add(areacode);
fieldDef.add(state);
fieldDef.add(dob);
fieldDef.add(ssn);

args.setFieldDefinition(fieldDef);
//set the modelid and the zingg dir
args.setModelId("100");
args.setZinggDir("models");
args.setNumPartitions(4);
args.setLabelDataSampleSize(0.5f);

//reading dataset into inputPipe and settint it up in 'args'
//below line should not be required if you are reading from in memory dataset
//in that case, replace df with input df
val df = spark.read.option("header", "true").csv("examples/febrl/test.csv")
import zingg.client.pipe.InMemoryPipe;
import java.util.HashMap

val inputPipe = new InMemoryPipe(df);
inputPipe.setProps(new HashMap[String, String]());
val pipes = Array[zingg.client.pipe.Pipe](inputPipe);
args.setData(pipes);

//setting outputpipe in 'args'
val outputPipe = new InMemoryPipe();
//outputPipe.setProps(new HashMap[String, String]());
val pipes = Array[zingg.client.pipe.Pipe](outputPipe);
args.setOutput(pipes);

val options = new ClientOptions("--phase", "match",  "--conf", "dummy", "--license", "dummy", "--email", "xxx@yyy.com");

//Zingg execution for the given phase
val client = new Client(args, options);
client.init();
client.execute();
//the output is in outputPipe.getRecords
outputPipe.getRecords().show()
