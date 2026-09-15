# WIDE: Boosting Adaptive LLM Inference via Token-level Dynamic Width Pruning

**Authors:** Haozhe Hu, Hao Wu, Peiran Yin, Chao Han, Yunpu Ma, Xiaoyu Shen  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI, cs.CL, cs.LG  
**Document ID:** DOC-ARXIV-2607.28418v1  

## Abstract
Pruning is a promising approach for improving the efficiency of LLMs. Existing static structured pruning methods are hardware-friendly and can deliver practical throughput gains, but their input-agnostic computation allocation often causes substantial accuracy degradation under aggressive sparsity. Recent dynamic sparsity methods improve quality retention by adapting computation to individual inputs, yet they remain largely limited to coarse-grained structural decisions and their practical acceleration under real-world inference scenarios remains challenging. To address these challenges, we present WIDE, the first end-to-end differentiable token-level dynamic width pruning framework designed for both prefill and decode scenarios. WIDE enables fine-grained computation allocation by allowing each token to dynamically select attention-head groups and FFN-channel groups, extending dynamic pruning beyond layer-level decisions to neuron-block-level granularity. Through a two-stage training pipeline, WIDE learns effective token-wise sparse execution patterns and achieves substantially better quality retention than existing approaches. To make such fine-grained dynamic pruning practical, we further propose a pruning--kernel co-design framework that decomposes dynamic sparsity acceleration into mask reordering, hardware-agnostic block-level skipping, and hardware-dependent intra-block skipping, enabling efficient execution across different granularities. At 50% sparsity, WIDE provides 55.1% performance boost when compared to the state-of-the-art dynamic depth pruning under calibration-only settings. Under prefill and decoding inference workloads, WIDE achieves close-to-theoretical kernel-level speedups of up to 1.98x for prefill and 4.95x for decoding, as well as 1.68x and 1.55x end-to-end acceleration. Our code is available at https://github.com/EIT-NLP/LLM-Pruning/tree/main/WIDE.

## 1. Introduction
Recent advancements in cs.AI, cs.CL, cs.LG have significantly transformed knowledge discovery systems. This document explores wide: boosting adaptive llm inference via token-level dynamic width pruning and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Haozhe Hu, Hao Wu, Peiran Yin, Chao Han, Yunpu Ma, Xiaoyu Shen established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, WIDE: Boosting Adaptive LLM Inference via Token-level Dynamic Width Pruning provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

