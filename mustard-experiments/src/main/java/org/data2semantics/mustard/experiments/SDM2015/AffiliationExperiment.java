package org.data2semantics.mustard.experiments.SDM2015;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import org.data2semantics.mustard.experiments.rescal.RESCALKernel;
import org.data2semantics.mustard.experiments.utils.AIFBDataSet;
import org.data2semantics.mustard.experiments.utils.BGSLithoDataSet;
import org.data2semantics.mustard.experiments.utils.ClassificationDataSet;
import org.data2semantics.mustard.experiments.utils.Result;
import org.data2semantics.mustard.experiments.utils.ResultsTable;
import org.data2semantics.mustard.experiments.utils.SimpleGraphKernelExperiment;
import org.data2semantics.mustard.kernels.data.GraphList;
import org.data2semantics.mustard.kernels.data.RDFData;
import org.data2semantics.mustard.kernels.data.SingleDTGraph;
import org.data2semantics.mustard.kernels.graphkernels.CombinedKernel;
import org.data2semantics.mustard.kernels.graphkernels.FeatureVectorKernel;
import org.data2semantics.mustard.kernels.graphkernels.GraphKernel;
import org.data2semantics.mustard.kernels.graphkernels.graphlist.PathCountKernel;
import org.data2semantics.mustard.kernels.graphkernels.graphlist.PathCountKernelMkII;
import org.data2semantics.mustard.kernels.graphkernels.graphlist.TreePathCountKernel;
import org.data2semantics.mustard.kernels.graphkernels.graphlist.WLSubTreeKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFPathCountKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFIntersectionTreeEdgeVertexPathKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFRootPathCountKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFTreePathCountKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFTreeWLSubTreeKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFWLRootSubTreeKernel;
import org.data2semantics.mustard.kernels.graphkernels.rdfdata.RDFWLSubTreeKernel;
import org.data2semantics.mustard.kernels.graphkernels.singledtgraph.RDFDTGraphWLSubTreeKernel;
import org.data2semantics.mustard.learners.evaluation.Accuracy;
import org.data2semantics.mustard.learners.evaluation.EvaluationFunction;
import org.data2semantics.mustard.learners.evaluation.EvaluationUtils;
import org.data2semantics.mustard.learners.evaluation.F1;
import org.data2semantics.mustard.learners.libsvm.LibSVMParameters;
import org.data2semantics.mustard.rdf.DataSetUtils;
import org.data2semantics.mustard.rdf.RDFDataSet;
import org.data2semantics.mustard.rdf.RDFFileDataSet;
import org.data2semantics.mustard.rdf.RDFUtils;
import org.data2semantics.mustard.util.Pair;
import org.data2semantics.mustard.weisfeilerlehman.StringLabel;
import org.nodes.DTGraph;
import org.nodes.DTNode;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.Value;
import org.openrdf.rio.RDFFormat;

public class AffiliationExperiment {
	private static String AIFB_FILE = "datasets/aifb-fixed_complete.n3";

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		RDFDataSet tripleStore = new RDFFileDataSet(AIFB_FILE, RDFFormat.N3);
		ClassificationDataSet ds = new AIFBDataSet(tripleStore);
		ds.create();

		List<EvaluationFunction> evalFuncs = new ArrayList<EvaluationFunction>();
		evalFuncs.add(new Accuracy());
		evalFuncs.add(new F1());

		ResultsTable resTable = new ResultsTable();
		resTable.setDigits(2);
		resTable.setSignificanceTest(ResultsTable.SigTest.PAIRED_TTEST);
		resTable.setpValue(0.05);
		resTable.setShowStdDev(true);

		long[] seeds = {11,21,31,41,51,61,71,81,91,101};
		double[] cs = {1,10,100,1000};	

		LibSVMParameters svmParms = new LibSVMParameters(LibSVMParameters.C_SVC, cs);
		svmParms.setNumFolds(10);

		boolean reverseWL = true; // WL should be in reverse mode, which means regular subtrees
		boolean[] inference = {false,true};

		int[] depths = {1,2,3};
		int[] pathDepths = {0,1,2,3,4,5,6};
		int[] iterationsWL = {0,1,2,3,4,5,6};

		boolean depthTimesTwo = true;



		RDFData data = ds.getRDFData();
		List<Double> target = ds.getTarget();

		computeGraphStatistics(tripleStore, ds, inference, depths);

		///* The baseline experiment, BoW (or BoL if you prefer)
		for (boolean inf : inference) {
			resTable.newRow("Baseline BoL: " + inf);		 
			for (int d : depths) {
				List<RDFWLSubTreeKernel> kernelsBaseline = new ArrayList<RDFWLSubTreeKernel>();	
				kernelsBaseline.add(new RDFWLSubTreeKernel(0, d, inf, reverseWL, false, true));

				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernelsBaseline, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* The baseline experiment, BoW (or BoL if you prefer) Tree variant
		for (boolean inf : inference) {
			resTable.newRow("Baseline BoL Tree: " + inf);		 
			for (int d : depths) {
				List<RDFTreeWLSubTreeKernel> kernelsBaseline = new ArrayList<RDFTreeWLSubTreeKernel>();	
				kernelsBaseline.add(new RDFTreeWLSubTreeKernel(0, d, inf, reverseWL, false, true));

				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernelsBaseline, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* Path Count Root
		for (boolean inf : inference) {
			resTable.newRow("Path Count through root: " + inf);		 
			for (int d : depths) {

				List<RDFRootPathCountKernel> kernels = new ArrayList<RDFRootPathCountKernel>();	

				if (depthTimesTwo) {
					kernels.add(new RDFRootPathCountKernel(d*2, d, true, inf, true));
				} else {
					for (int dd : iterationsWL) {
						kernels.add(new RDFRootPathCountKernel(dd, d, true, inf, true));
					}
				}				

				//Collections.shuffle(target);
				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernels, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* WL Root
		for (boolean inf : inference) {
			resTable.newRow("WL through root: " + inf);		 
			for (int d : depths) {

				List<RDFWLRootSubTreeKernel> kernels = new ArrayList<RDFWLRootSubTreeKernel>();	

				if (depthTimesTwo) {
					kernels.add(new RDFWLRootSubTreeKernel(d*2, d, inf, reverseWL, false, true));
				} else {
					for (int dd : iterationsWL) {
						kernels.add(new RDFWLRootSubTreeKernel(dd, d, inf, reverseWL, false, true));
					}
				}

				//Collections.shuffle(target);
				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernels, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* Path Count Tree
		for (boolean inf : inference) {
			resTable.newRow("Path Count Tree: " + inf);		 
			for (int d : depths) {

				List<RDFTreePathCountKernel> kernels = new ArrayList<RDFTreePathCountKernel>();	

				if (depthTimesTwo) {
					kernels.add(new RDFTreePathCountKernel(d*2, d, inf, true));
				} else {
					for (int dd : iterationsWL) {
						kernels.add(new RDFTreePathCountKernel(dd, d, inf, true));
					}
				}

				//Collections.shuffle(target);
				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernels, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* WL Tree
		for (boolean inf : inference) {
			resTable.newRow("WL Tree: " + inf);		 
			for (int d : depths) {

				List<RDFTreeWLSubTreeKernel> kernels = new ArrayList<RDFTreeWLSubTreeKernel>();	

				if (depthTimesTwo) {
					kernels.add(new RDFTreeWLSubTreeKernel(d*2, d, inf, reverseWL, false, true));
				} else {
					for (int dd : iterationsWL) {
						kernels.add(new RDFTreeWLSubTreeKernel(dd, d, inf, reverseWL, false, true));
					}
				}

				//Collections.shuffle(target);
				SimpleGraphKernelExperiment<RDFData> exp = new SimpleGraphKernelExperiment<RDFData>(kernels, data, target, svmParms, seeds, evalFuncs);

				exp.run();

				for (Result res : exp.getResults()) {
					resTable.addResult(res);
				}
			}
		}
		//*/

		///* Regular WL
		for (boolean inf : inference) {
			resTable.newRow("Regular WL: " + inf);		
			for (int d : depths) {

				Set<Statement> st = RDFUtils.getStatements4Depth(tripleStore, ds.getRDFData().getInstances(), d, inf);
				st.removeAll(ds.getRDFData().getBlackList());
				DTGraph<String,String> graph = RDFUtils.statements2Graph(st, RDFUtils.REGULAR_LITERALS);
				List<DTNode<String,String>> instanceNodes = RDFUtils.findInstances(graph, ds.getRDFData().getInstances());
				graph = RDFUtils.simplifyInstanceNodeLabels(graph, instanceNodes);
				List<DTGraph<String,String>> graphs = RDFUtils.getSubGraphs(graph, instanceNodes, d);

				double avgNodes = 0;
				double avgLinks = 0;

				for (DTGraph<String,String> g : graphs){
					avgNodes += g.nodes().size();
					avgLinks += g.links().size();
				}
				avgNodes /= graphs.size();
				avgLinks /= graphs.size();

				System.out.println("Avg # nodes: " + avgNodes + " , avg # links: " + avgLinks);

				List<WLSubTreeKernel> kernels = new ArrayList<WLSubTreeKernel>();

				if (depthTimesTwo) {
					WLSubTreeKernel kernel = new WLSubTreeKernel(d*2, reverseWL, true);			
					kernels.add(kernel);
				} else {
					for (int dd : iterationsWL) {
						WLSubTreeKernel kernel = new WLSubTreeKernel(dd, reverseWL, true);			
						kernels.add(kernel);
					}
				}

				//resTable.newRow(kernels.get(0).getLabel() + "_" + inf);
				SimpleGraphKernelExperiment<GraphList<DTGraph<String,String>>> exp2 = new SimpleGraphKernelExperiment<GraphList<DTGraph<String,String>>>(kernels, new GraphList<DTGraph<String,String>>(graphs), target, svmParms, seeds, evalFuncs);

				//System.out.println(kernels.get(0).getLabel());
				exp2.run();

				for (Result res : exp2.getResults()) {
					resTable.addResult(res);
				}
				System.out.println(resTable);
			}
		}
		//*/

		resTable.addCompResults(resTable.getBestResults());
		System.out.println(resTable);

		///* Path Count full
		for (boolean inf : inference) {
			resTable.newRow("Path Count Full: " + inf);		
			for (int d : depths) {

				Set<Statement> st = RDFUtils.getStatements4Depth(tripleStore, ds.getRDFData().getInstances(), d, inf);
				st.removeAll(ds.getRDFData().getBlackList());
				DTGraph<String,String> graph = RDFUtils.statements2Graph(st, RDFUtils.REGULAR_LITERALS);
				List<DTNode<String,String>> instanceNodes = RDFUtils.findInstances(graph, ds.getRDFData().getInstances());
				graph = RDFUtils.simplifyInstanceNodeLabels(graph, instanceNodes);
				List<DTGraph<String,String>> graphs = RDFUtils.getSubGraphs(graph, instanceNodes, d);

				double avgNodes = 0;
				double avgLinks = 0;

				for (DTGraph<String,String> g : graphs){
					avgNodes += g.nodes().size();
					avgLinks += g.links().size();
				}
				avgNodes /= graphs.size();
				avgLinks /= graphs.size();

				System.out.println("Avg # nodes: " + avgNodes + " , avg # links: " + avgLinks);

				List<PathCountKernel> kernels = new ArrayList<PathCountKernel>();

				if (depthTimesTwo) {
					PathCountKernel kernel = new PathCountKernel(d*2, true);			
					kernels.add(kernel);
				} else {
					for (int dd : iterationsWL) {
						PathCountKernel kernel = new PathCountKernel(dd, true);			
						kernels.add(kernel);
					}
				}

				//resTable.newRow(kernels.get(0).getLabel() + "_" + inf);
				SimpleGraphKernelExperiment<GraphList<DTGraph<String,String>>> exp2 = new SimpleGraphKernelExperiment<GraphList<DTGraph<String,String>>>(kernels, new GraphList<DTGraph<String,String>>(graphs), target, svmParms, seeds, evalFuncs);

				//System.out.println(kernels.get(0).getLabel());
				exp2.run();

				for (Result res : exp2.getResults()) {
					resTable.addResult(res);
				}
				System.out.println(resTable);
			}
		}
		//*/



		resTable.addCompResults(resTable.getBestResults());
		System.out.println(resTable);
	}

	private static void computeGraphStatistics(RDFDataSet tripleStore, ClassificationDataSet ds, boolean[] inference, int[] depths) {
		Map<Boolean, Map<Integer, Pair<Double, Double>>> stats = new HashMap<Boolean, Map<Integer, Pair<Double, Double>>>();


		for (boolean inf : inference) {
			stats.put(inf, new HashMap<Integer, Pair<Double, Double>>());
			for (int depth : depths) {

				Set<Statement> st = RDFUtils.getStatements4Depth(tripleStore, ds.getRDFData().getInstances(), depth, inf);
				st.removeAll(ds.getRDFData().getBlackList());
				DTGraph<String,String> graph = RDFUtils.statements2Graph(st, RDFUtils.REGULAR_LITERALS);
				List<DTNode<String,String>> instanceNodes = RDFUtils.findInstances(graph, ds.getRDFData().getInstances());
				graph = RDFUtils.simplifyInstanceNodeLabels(graph, instanceNodes);
				List<DTGraph<String,String>> graphs = RDFUtils.getSubGraphs(graph, instanceNodes, depth);

				double v = 0;
				double e = 0;
				for (DTGraph<String,String> g : graphs) {
					v += g.nodes().size();
					e += g.links().size();
				}
				v /= graphs.size();
				e /= graphs.size();

				stats.get(inf).put(depth, new Pair<Double,Double>(v,e));
			}

		}

		for (boolean k1 : stats.keySet()) {
			System.out.println("Inference: " + k1);
			for (int k2 : stats.get(k1).keySet()) {
				System.out.println("Depth " + k2 + ", vertices: " + (stats.get(k1).get(k2).getFirst()) + " , edges: " + (stats.get(k1).get(k2).getSecond()));
			}
		}
	}
}