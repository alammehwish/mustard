package org.data2semantics.mustard.kernels.graphkernels.singledtgraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.data2semantics.mustard.kernels.ComputationTimeTracker;
import org.data2semantics.mustard.kernels.KernelUtils;
import org.data2semantics.mustard.kernels.data.SingleDTGraph;
import org.data2semantics.mustard.kernels.graphkernels.FeatureVectorKernel;
import org.data2semantics.mustard.kernels.graphkernels.GraphKernel;
import org.data2semantics.mustard.learners.SparseVector;
import org.data2semantics.mustard.utils.Pair;
import org.data2semantics.mustard.weisfeilerlehman.MapLabel;
import org.data2semantics.mustard.weisfeilerlehman.WeisfeilerLehmanApproxDTGraphMapLabelIterator;
import org.data2semantics.mustard.weisfeilerlehman.WeisfeilerLehmanApproxIterator;
import org.nodes.DTGraph;
import org.nodes.DTLink;
import org.nodes.DTNode;
import org.nodes.LightDTGraph;

/**
 * @author Gerben
 *
 */
public class DTGraphTreeWLSubTreeApproxKernel implements GraphKernel<SingleDTGraph>, FeatureVectorKernel<SingleDTGraph>, ComputationTimeTracker {

	private Map<DTNode<MapLabel,MapLabel>, List<Pair<DTNode<MapLabel,MapLabel>, Integer>>> instanceVertexIndexMap;
	private Map<DTNode<MapLabel,MapLabel>, List<Pair<DTLink<MapLabel,MapLabel>, Integer>>> instanceEdgeIndexMap;

	private DTGraph<MapLabel,MapLabel> rdfGraph;
	private List<DTNode<MapLabel,MapLabel>> instanceVertices;
	
	private Map<String, Double> labelFreq;

	private int depth;
	private int iterations;
	private boolean normalize;
	private boolean reverse;
	private boolean iterationWeighting;
	private boolean trackPrevNBH;
	private int maxLabelCard;
	private double minFreq;
	
	private long compTime; // should be last, so that it is the last arg in the label

	public DTGraphTreeWLSubTreeApproxKernel(int iterations, int depth, boolean reverse, boolean iterationWeighting, boolean trackPrevNBH, int maxLabelCard, double minFreq, boolean normalize) {
		this.reverse = reverse;
		this.iterationWeighting = iterationWeighting;
		this.trackPrevNBH = trackPrevNBH;
		this.normalize = normalize;
		this.depth = depth;
		this.iterations = iterations;
		this.maxLabelCard = maxLabelCard;
		this.minFreq = minFreq;
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
		this.instanceVertices       = new ArrayList<DTNode<MapLabel,MapLabel>>();
		this.instanceVertexIndexMap = new HashMap<DTNode<MapLabel,MapLabel>, List<Pair<DTNode<MapLabel,MapLabel>, Integer>>>();
		this.instanceEdgeIndexMap   = new HashMap<DTNode<MapLabel,MapLabel>, List<Pair<DTLink<MapLabel,MapLabel>, Integer>>>();

		SparseVector[] featureVectors = new SparseVector[data.getInstances().size()];
		for (int i = 0; i < featureVectors.length; i++) {
			featureVectors[i] = new SparseVector();
		}	

		init(data.getGraph(), data.getInstances());
		WeisfeilerLehmanApproxIterator<DTGraph<MapLabel,MapLabel>, String> wl = new WeisfeilerLehmanApproxDTGraphMapLabelIterator(reverse, trackPrevNBH, maxLabelCard, minFreq);

		List<DTGraph<MapLabel,MapLabel>> gList = new ArrayList<DTGraph<MapLabel,MapLabel>>();
		gList.add(rdfGraph);
		
		long tic = System.currentTimeMillis();

		wl.wlInitialize(gList);

		double weight = 1.0;
		if (iterationWeighting) {
			weight = Math.sqrt(1.0 / (iterations + 1));
		}

		// ComputeFVs also computes the labelFreq's, from a design perspective might be cleaner to put it in a separate method
		computeFVs(rdfGraph, instanceVertices, weight, featureVectors, wl.getLabelDict().size()-1);

		for (int i = 0; i < iterations; i++) {
			if (iterationWeighting) {
				weight = Math.sqrt((2.0 + i) / (iterations + 1));
			}
			wl.wlIterate(gList, labelFreq);
			computeFVs(rdfGraph, instanceVertices, weight, featureVectors, wl.getLabelDict().size()-1);
		}
		
		compTime = System.currentTimeMillis() - tic;
		
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



	private void init(DTGraph<String,String> graph, List<DTNode<String,String>> instances) {
		DTNode<MapLabel,MapLabel> startV;
		List<DTNode<String,String>> frontV, newFrontV;
		List<Pair<DTNode<MapLabel,MapLabel>, Integer>> vertexIndexMap;
		List<Pair<DTLink<MapLabel,MapLabel>, Integer>> edgeIndexMap;
		Map<DTNode<String,String>, DTNode<MapLabel,MapLabel>> vOldNewMap = new HashMap<DTNode<String,String>,DTNode<MapLabel,MapLabel>>();
		Map<DTLink<String,String>, DTLink<MapLabel,MapLabel>> eOldNewMap = new HashMap<DTLink<String,String>,DTLink<MapLabel,MapLabel>>();

		rdfGraph = new LightDTGraph<MapLabel,MapLabel>();

		for (DTNode<String,String> oldStartV : instances) {				
			vertexIndexMap = new ArrayList<Pair<DTNode<MapLabel,MapLabel>, Integer>>();
			edgeIndexMap   = new ArrayList<Pair<DTLink<MapLabel,MapLabel>, Integer>>();

			// Get the start node
			if (vOldNewMap.containsKey(oldStartV)) {
				startV = vOldNewMap.get(oldStartV);
			} else { 
				startV = rdfGraph.add(new MapLabel());
				vOldNewMap.put(oldStartV, startV);
			}
			startV.label().append(depth, oldStartV.label());
			instanceVertices.add(startV);

			instanceVertexIndexMap.put(startV, vertexIndexMap);
			instanceEdgeIndexMap.put(startV, edgeIndexMap);

			frontV = new ArrayList<DTNode<String,String>>();
			frontV.add(oldStartV);

			// Process the start node
			vertexIndexMap.add(new Pair<DTNode<MapLabel,MapLabel>,Integer>(startV, depth));

			for (int j = depth - 1; j >= 0; j--) {
				newFrontV = new ArrayList<DTNode<String,String>>();
				for (DTNode<String,String> qV : frontV) {
					for (DTLink<String,String> edge : qV.linksOut()) {
						if (vOldNewMap.containsKey(edge.to())) { // This vertex has been added to rdfGraph
							vertexIndexMap.add(new Pair<DTNode<MapLabel,MapLabel>,Integer>(vOldNewMap.get(edge.to()), j));  
							vOldNewMap.get(edge.to()).label().append(j, edge.to().label()); 
						} else {
							DTNode<MapLabel,MapLabel> newN = rdfGraph.add(new MapLabel());
							newN.label().append(j, edge.to().label());
							vOldNewMap.put(edge.to(), newN);
							vertexIndexMap.add(new Pair<DTNode<MapLabel,MapLabel>,Integer>(newN, j)); 
						}

						if (eOldNewMap.containsKey(edge)) {
							edgeIndexMap.add(new Pair<DTLink<MapLabel,MapLabel>,Integer>(eOldNewMap.get(edge),j)); 		
							eOldNewMap.get(edge).tag().append(j, edge.tag());
						} else {
							DTLink<MapLabel,MapLabel> newE = vOldNewMap.get(qV).connect(vOldNewMap.get(edge.to()), new MapLabel());
							newE.tag().append(j, edge.tag());
							eOldNewMap.put(edge, newE);
							edgeIndexMap.add(new Pair<DTLink<MapLabel,MapLabel>,Integer>(newE, j));
						}

						// Add the vertex to the new front, if we go into a new round
						if (j > 0) {
							newFrontV.add(edge.to());
						}
					}
				}
				frontV = newFrontV;
			}
		}		
	}





	/**
	 * The computation of the feature vectors assumes that each edge and vertex is only processed once. We can encounter the same
	 * vertex/edge on different depths during computation, this could lead to multiple counts of the same vertex, possibly of different
	 * depth labels.
	 * 
	 * @param graph
	 * @param instances
	 * @param weight
	 * @param featureVectors
	 */
	private void computeFVs(DTGraph<MapLabel,MapLabel> graph, List<DTNode<MapLabel,MapLabel>> instances, double weight, SparseVector[] featureVectors, int lastIndex) {
		int index;
		List<Pair<DTNode<MapLabel,MapLabel>, Integer>> vertexIndexMap;
		List<Pair<DTLink<MapLabel,MapLabel>, Integer>> edgeIndexMap;
		
		// Build a new label Frequencies map
		labelFreq = new HashMap<String, Double>();

		for (int i = 0; i < instances.size(); i++) {
			featureVectors[i].setLastIndex(lastIndex);
			Set<String> seen = new HashSet<String>(); // to track seen label for this instance

			vertexIndexMap = instanceVertexIndexMap.get(instances.get(i));
			for (Pair<DTNode<MapLabel,MapLabel>, Integer> vertex : vertexIndexMap) {
				String lab = vertex.getFirst().label().get(vertex.getSecond());
				if (!vertex.getFirst().label().getSameAsPrev(vertex.getSecond())) {
					index = Integer.parseInt(lab);
					featureVectors[i].setValue(index, featureVectors[i].getValue(index) + weight);
				}
				// Count
				if (!labelFreq.containsKey(lab)) {
					labelFreq.put(lab, 0.0);
				} 
				if (!seen.contains(lab)) {
					labelFreq.put(lab, labelFreq.get(lab) + 1);
					seen.add(lab);
				}
			}
			edgeIndexMap = instanceEdgeIndexMap.get(instances.get(i));
			for (Pair<DTLink<MapLabel,MapLabel>, Integer> edge : edgeIndexMap) {
				String lab = edge.getFirst().tag().get(edge.getSecond());
				if (!edge.getFirst().tag().getSameAsPrev(edge.getSecond())) {
					index = Integer.parseInt(lab);
					featureVectors[i].setValue(index, featureVectors[i].getValue(index) + weight);
				}
				// Count
				if (!labelFreq.containsKey(lab)) {
					labelFreq.put(lab, 0.0);
				} 
				if (!seen.contains(lab)) {
					labelFreq.put(lab, labelFreq.get(lab) + 1);
					seen.add(lab);
				}
			}
		}
		// normalize to occur/num instances.
		for (String k : labelFreq.keySet()) {
			labelFreq.put(k, labelFreq.get(k) / (double) instances.size());
		}
	}
}
