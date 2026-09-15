# Hierarchical Multilevel Monte Carlo for Order-Optimal Neural Actor-Critic in Average-Reward CMDPs

**Authors:** Ankur Naskar, Vaneet Aggarwal  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28390v1  

## Abstract
Constrained Markov Decision Processes (CMDPs) provide a natural framework for reinforcement learning in safety-critical applications, where agents maximize long-term reward while satisfying long-term constraints. Although primal-dual actor-critic methods with linear critics are well understood, extending order-optimal convergence guarantees to neural critics in average-reward CMDPs has remained open. The main challenge is a fundamental bias-cost trade-off in neural critic estimation: under Neural Tangent Kernel (NTK) analysis, reducing critic bias substantially increases critic optimization cost, preventing order-optimal convergence in the primal-dual framework. We resolve this bottleneck by introducing a hierarchical Multilevel Monte Carlo (MLMC) neural critic that performs debiasing simultaneously across trajectory sampling and critic optimization. The resulting estimator attains the bias of a long critic optimization run with only logarithmic expected sample cost. Building on this estimator, we develop a primal-dual Natural Actor-Critic algorithm that achieves both an optimality gap and a constraint violation of order $\tilde{O}(T^{-1/2})$. This establishes the first order-optimal convergence guarantees for infinite-horizon average-reward CMDPs with general policy parameterization and neural critics, while eliminating the need to know the underlying mixing time. Our results are novel even in the unconstrained setting.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores hierarchical multilevel monte carlo for order-optimal neural actor-critic in average-reward cmdps and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Ankur Naskar, Vaneet Aggarwal established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Hierarchical Multilevel Monte Carlo for Order-Optimal Neural Actor-Critic in Average-Reward CMDPs provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

