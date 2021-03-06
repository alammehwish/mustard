package org.data2semantics.mustard.kernels.graphkernels.singledtgraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.data2semantics.mustard.kernels.ComputationTimeTracker;
import org.data2semantics.mustard.kernels.FeatureInspector;
import org.data2semantics.mustard.kernels.KernelUtils;
import org.data2semantics.mustard.kernels.SparseVector;
import org.data2semantics.mustard.kernels.data.SingleDTGraph;
import org.data2semantics.mustard.kernels.graphkernels.FeatureVectorKernel;
import org.data2semantics.mustard.kernels.graphkernels.GraphKernel;
import org.data2semantics.mustard.utils.WalkCountUtils;
import org.nodes.DTGraph;
import org.nodes.DTLink;
import org.nodes.DTNode;
import org.nodes.LightDTGraph;

/**
 * 
 * Tree version (i.e. walks are counted in the Tree representation of the neighborhood) of {@link org.data2semantics.mustard.kernels.graphkernels.singledtgraph.DTGraphWalkCountKernel}.
 * 
 * @author Gerben
 *
 */
public class DTGraphTreeWalkCountKernel implements GraphKernel<SingleDTGraph>, FeatureVectorKernel<SingleDTGraph>, ComputationTimeTracker, FeatureInspector {

	private DTGraph<String,String> rdfGraph;
	private List<DTNode<String,String>> instanceVertices;

	private int pathLength;
	private int depth;
	private boolean normalize;
	private long compTime;

	private Map<String, Integer> pathDict;
	private Map<String, Integer> labelDict;
	private Map<Integer, String> reversePathDict;
	private Map<Integer, String> reverseLabelDict;



	public DTGraphTreeWalkCountKernel(int pathLength, int depth, boolean normalize) {
		this.normalize = normalize;
		this.pathLength = pathLength;
		this.depth = depth;
	}

	public String getLabel() {
		return KernelUtils.createLabel(this);		
	}

	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}

	public long getComputationTime() {
		return compTime;
	}

	public SparseVector[] computeFeatureVectors(SingleDTGraph data) {
		pathDict  = new HashMap<String, Integer>();
		labelDict = new HashMap<String, Integer>();
		init(data.getGraph(), data.getInstances());

		List<DTNode<String,String>> sf,nsf;
		List<DTLink<String,String>> sflinks;

		if (pathLength > depth * 2) { // cannot count paths longer than 2 times the depth (not a typical parameter scenario though).
			pathLength = depth * 2;
		}
		
		SparseVector[] featureVectors = new SparseVector[data.numInstances()];
		for (int i = 0; i < featureVectors.length; i++) {
			featureVectors[i] = new SparseVector();
		}

		long tic = System.currentTimeMillis();
		
		// Initialize and compute the featureVectors
		for (int i = 0; i < featureVectors.length; i++) {
			countPathRec(featureVectors[i], instanceVertices.get(i), "", pathLength);

			sf = new ArrayList<DTNode<String,String>>();
			for (DTLink<String,String> e : instanceVertices.get(i).linksOut()) {
				sf.add(e.to());
				//sf.addAll(instanceVertices.get(i).out());
			}

			sflinks = new ArrayList<DTLink<String,String>>();
			sflinks.addAll(instanceVertices.get(i).linksOut());		
	
			int currentPL = pathLength-1;
			for (int d = depth; d > 0; d--, currentPL = currentPL - 2) {
				currentPL = Math.min((d * 2) - 1, currentPL);
				for (DTLink<String,String> e : sflinks) {
					countPathRec(featureVectors[i], e, "", currentPL);
				}
				sflinks = new ArrayList<DTLink<String,String>>();
				nsf = new ArrayList<DTNode<String,String>>();

				for (DTNode<String,String> n : sf) {
					countPathRec(featureVectors[i], n, "", currentPL-1);
					if (currentPL - 1 > 0) {
						sflinks.addAll(n.linksOut());
						for (DTLink<String,String> e : n.linksOut()) {
							nsf.add(e.to());
						}
					}
				}
				sf = nsf;
			}		
		}

		compTime = System.currentTimeMillis() - tic;
		
		reversePathDict = new HashMap<Integer,String>();	
		for (String key : pathDict.keySet()) {
			reversePathDict.put(pathDict.get(key), key);
		}
		
		reverseLabelDict = new HashMap<Integer,String>();	
		for (String key : labelDict.keySet()) {
			reverseLabelDict.put(labelDict.get(key), key);
		}

		if (this.normalize) {
			featureVectors = KernelUtils.normalize(featureVectors);
		}

		return featureVectors;
	}


	public double[][] compute(SingleDTGraph data) {
		SparseVector[] featureVectors = computeFeatureVectors(data);
		double[][] kernel = KernelUtils.initMatrix(data.getInstances().size(), data.getInstances().size());
		long tic = System.currentTimeMillis();
		kernel = KernelUtils.computeKernelMatrix(featureVectors, kernel);
		compTime += System.currentTimeMillis() - tic;
		return kernel;
	}


	private void countPathRec(SparseVector fv, DTNode<String,String> vertex, String path, int depth) {
		// Count path
		path = path + vertex.label();

		if (!pathDict.containsKey(path)) {
			pathDict.put(path, pathDict.size());
		}
		fv.setValue(pathDict.get(path), fv.getValue(pathDict.get(path)) + 1);

		if (depth > 0) {
			for (DTLink<String,String> edge : vertex.linksOut()) {
				countPathRec(fv, edge, path, depth-1);
			}
		}	
	}

	private void countPathRec(SparseVector fv, DTLink<String,String> edge, String path, int depth) {
		// Count path
		path = path + edge.tag();

		if (!pathDict.containsKey(path)) {
			pathDict.put(path, pathDict.size());
		}
		fv.setValue(pathDict.get(path), fv.getValue(pathDict.get(path)) + 1);

		if (depth > 0) {
			countPathRec(fv, edge.to(), path, depth-1);
		}	
	}


	private void init(DTGraph<String,String> graph, List<DTNode<String,String>> instances) {
		rdfGraph = new LightDTGraph<String,String>();
		instanceVertices = new ArrayList<DTNode<String,String>>();
		Map<DTNode<String,String>, Integer> instanceIndexMap = new HashMap<DTNode<String,String>, Integer>();

		for (int i = 0; i < instances.size(); i++) {
			instanceIndexMap.put(instances.get(i), i);
			instanceVertices.add(null);
		}

		for (DTNode<String,String> vertex : graph.nodes()) {
			if (!labelDict.containsKey(vertex.label())) {
				labelDict.put(vertex.label(), labelDict.size());
			}
			String lab = "_" + Integer.toString(labelDict.get(vertex.label()));

			if (instanceIndexMap.containsKey(vertex)) {
				instanceVertices.set(instanceIndexMap.get(vertex), rdfGraph.add(lab));
			} else {
				rdfGraph.add(lab);
			}


		}
		for (DTLink<String,String> edge : graph.links()) {
			if (!labelDict.containsKey(edge.tag())) {
				labelDict.put(edge.tag(), labelDict.size());
			}
			String lab = "_" + Integer.toString(labelDict.get(edge.tag()));

			rdfGraph.nodes().get(edge.from().index()).connect(rdfGraph.nodes().get(edge.to().index()), lab); // ?
		}	
	}

	public List<String> getFeatureDescriptions(List<Integer> indicesSV) {
		if (labelDict == null) {
			throw new RuntimeException("Should run computeFeatureVectors first");
		} else {
			List<String> desc = new ArrayList<String>();
			
			for (int index : indicesSV) {
				desc.add(WalkCountUtils.getFeatureDecription(reverseLabelDict, reversePathDict, index));
			}
			return desc;
		}
	}
}
