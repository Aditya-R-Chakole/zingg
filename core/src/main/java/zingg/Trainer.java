package zingg;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import zingg.block.Canopy;
import zingg.block.Tree;
import zingg.model.Model;
import zingg.spark.util.BlockingTreeUtil;
import zingg.client.ZFrame;
import zingg.client.ZinggClientException;
import zingg.client.ZinggOptions;
import zingg.util.Analytics;
import zingg.client.util.ColName;
import zingg.client.util.ColValues;
import zingg.util.Metric;
import zingg.util.DSUtil;
import zingg.util.ModelUtil;
import zingg.util.PipeUtilBase;

public abstract class Trainer<S,D,R,C> extends ZinggBase<S,D,R,C>{

	protected static String name = "zingg.Trainer";
	public static final Log LOG = LogFactory.getLog(Trainer.class);    

    
	public void execute() throws ZinggClientException {
        try {
			LOG.info("Reading inputs for training phase ...");
			LOG.info("Initializing learning similarity rules");
			
			ZFrame<D,R,C> positives = null;
			ZFrame<D,R,C> negatives = null;
			ZFrame<D,R,C> tra = getDSUtil().getTraining(getPipeUtil(), args);
			tra = getDSUtil().joinWithItself(tra, ColName.CLUSTER_COLUMN, true);
			tra = tra.cache();
			positives = tra.filter(tra.equalTo(ColName.MATCH_FLAG_COL, ColValues.MATCH_TYPE_MATCH));
			negatives = tra.filter(tra.equalTo(ColName.MATCH_FLAG_COL, ColValues.MATCH_TYPE_NOT_A_MATCH));
			LOG.warn("Training on positive pairs - " + positives.count());
			LOG.warn("Training on negative pairs - " + negatives.count());
				
			ZFrame<D,R,C> testData = getPipeUtil().read(true, args.getNumPartitions(), false, args.getData());
			Tree<Canopy> blockingTree = BlockingTreeUtil.createBlockingTreeFromSample(testData,  positives, 0.5,
					-1, args, hashFunctions);
			if (blockingTree == null || blockingTree.getSubTrees() == null) {
				LOG.warn("Seems like no indexing rules have been learnt");
			}
			BlockingTreeUtil.writeBlockingTree(spark, ctx, blockingTree, args);
			LOG.info("Learnt indexing rules and saved output at " + args.getZinggDir());
			// model
			Model model = ModelUtil.createModel(positives, negatives, new Model(this.featurers), spark);
			model.save(args.getModel());
			LOG.info("Learnt similarity rules and saved output at " + args.getZinggDir());
			Analytics.track(Metric.TRAINING_MATCHES, Metric.approxCount(positives), args.getCollectMetrics());
			Analytics.track(Metric.TRAINING_NONMATCHES, Metric.approxCount(negatives), args.getCollectMetrics());
			LOG.info("Finished Learning phase");			
		} catch (Exception e) {
			e.printStackTrace();
			throw new ZinggClientException(e.getMessage());
		}
    }

		    
}
