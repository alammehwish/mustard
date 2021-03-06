package org.data2semantics.mustard.kernels.graphkernels.singledtgraph;


import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.data2semantics.mustard.kernels.KernelUtils;
import org.data2semantics.mustard.kernels.data.SingleDTGraph;
import org.data2semantics.mustard.kernels.graphkernels.GraphKernel;
import org.data2semantics.mustard.utils.HubUtils;
import org.data2semantics.mustard.utils.LabelTagPair;


/**
 * Wrapper that performs hub removal before computing the provided GraphKernel<SingleDTGraph>.
 * The minHubSize can be supplied as a single value or as an array. If an array is provided than a graph with hubs removed is computed for each setting and the provided kernel is computed on this graph.
 * The kernel for each setting are summed together into one kernel.
 * <ul>
 * <li>minHubSize is the minimum hub size (in terms of links) that a hub has to be before it is removed.
 * </ul>
 * 
 * @author Gerben
 *
 * @param <K> the kernel to compute on the graph with hub removed
 */
public class DTGraphHubRemovalWrapperKernel<K extends GraphKernel<SingleDTGraph>> implements GraphKernel<SingleDTGraph> {
	private boolean normalize;
	private int[] minHubSizes;
	private K kernel;
	
	public DTGraphHubRemovalWrapperKernel(K kernel, int[] minHubSizes, boolean normalize) {
		this.normalize = normalize;
		this.minHubSizes = minHubSizes;
		this.kernel = kernel;
	}

	public DTGraphHubRemovalWrapperKernel(K kernel, int minHubSize, boolean normalize) {
		this.normalize = normalize;
		this.minHubSizes = new int[1];
		this.minHubSizes[0] = minHubSize;
		this.kernel = kernel;
	}

	public String getLabel() {
		return KernelUtils.createLabel(this) +"_"+ kernel.getLabel();		
	}

	public void setNormalize(boolean normalize) {
		this.normalize = normalize;
	}

	public double[][] compute(SingleDTGraph data) {
		double[][] matrix = KernelUtils.initMatrix(data.numInstances(), data.numInstances());
		
		Map<LabelTagPair<String,String>, Integer> hubs = HubUtils.countLabelTagPairs(data);
		
		List<Entry<LabelTagPair<String,String>, Integer>> sorted = HubUtils.sortHubMap(hubs);	
		
	
		for (int minCount : minHubSizes) {
			Map<LabelTagPair<String,String>, Integer> hubMap = HubUtils.createHubMapFromSortedLabelTagPairsMinCount(sorted, minCount);	
			SingleDTGraph g = HubUtils.removeHubs(data, hubMap);

			matrix = KernelUtils.sum(matrix, kernel.compute(g));
		}

		if (this.normalize) {
			matrix = KernelUtils.normalize(matrix);
		}
		return matrix;
	}
}
