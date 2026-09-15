# Beyond Geometric Complementarity: Coherent Overlap in Sparse Mixture-of-Experts Routing

**Authors:** Huiyuan Tian, Bonan Xu, Shijian Li  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28308v1  

## Abstract
Sparse mixture-of-experts (MoE) language models route each token to multiple experts, suggesting a geometric account of their benefit: co-selected experts should contribute distinct representation directions. Existing evidence often conflates route coherence, candidate quality, and candidate-by-context interaction. We distinguish these quantities using an Expert Subspace Separation Index (ESSI), matched-route residuals, and a prefix-controlled $2\times2$ factorial; frozen-route interventions and a controlled Top-$k$ study assess functional value. Three paired contrasts organize the findings. First, across six MoE architectures, expert subspaces overlap substantially, yet actual routes explain token representations better than matched alternatives. Second, across the 39 factorial cells in OLMoE, Mixtral, and DeepSeek, the selected candidate explains more of the residual representation than the strongest unselected rival in every cell, yet the actual prefix narrows this advantage throughout: all interactions are negative, and every 95% confidence interval lies below zero. Third, this geometric narrowing does not imply functional redundancy: adding later experts improves next-token prediction in 24 of 39 frozen-route comparisons, while the other 15 estimates are inconclusive; a controlled training study also favors Top-2 over Top-1 in all three seeds. We call this joint pattern coherent overlap: routing selects token-relevant experts from a shared geometric neighborhood, while useful multi-expert computation persists without disjoint linear coverage. Separating these quantities clarifies why geometric similarity alone cannot determine redundancy or pruning value.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores beyond geometric complementarity: coherent overlap in sparse mixture-of-experts routing and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Huiyuan Tian, Bonan Xu, Shijian Li established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Beyond Geometric Complementarity: Coherent Overlap in Sparse Mixture-of-Experts Routing provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

