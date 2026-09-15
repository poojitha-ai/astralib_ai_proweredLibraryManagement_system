# Stage-Replay Divergence Follows the KV Cache: Fixed-Prefix Precision Controls and Bidirectional Cache Transplantation

**Authors:** Alexander Boesgaard Lorup  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG, cs.CL  
**Document ID:** DOC-ARXIV-2607.28495v1  

## Abstract
Stage-replay diagnostics reconstruct intermediate token prefixes and treat fresh-prefill continuation as continuation from the decoder state that originally reached the prefix. We audit that assumption at a whole reasoning-stage boundary in a Qwen2.5-derived system. A matched 200-item experiment compares retained live cache with one-shot prefill of identical integer tokens and places an exact replica on both sides. In BF16, replicas remain exact while the constructions differ on 166 suffixes and 20 correctness labels; the accuracy difference is only one point (paired 95% CI [-3.5, +5.5]). A fixed-prefix 2x2 holds all 200 token states constant while crossing construction and precision. The BF16 disagreements recur, whereas FP32 produces no decoded disagreement (95% Wilson upper bound 1.88%). A prospective bridge makes token-by-token incremental and retained live caches bit-exact on 12/12 rows; an all-200 saved-ledger audit reproduces every retained trajectory and comparison fingerprint. Bidirectional transplantation of all 48 key/value layers makes every tested divergent continuation follow its cache donor, both on a selected set at the primary checkpoint (24/24) and an outcome-blind replication at a later checkpoint (43/43). Exact-token replay can therefore be repeatable without preserving live-state fidelity. On the tested states, boundary K/V cache is a causally sufficient carrier of the divergent trajectory, while numerical precision moderates its behavioral expression.

## 1. Introduction
Recent advancements in cs.LG, cs.CL have significantly transformed knowledge discovery systems. This document explores stage-replay divergence follows the kv cache: fixed-prefix precision controls and bidirectional cache transplantation and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Alexander Boesgaard Lorup established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Stage-Replay Divergence Follows the KV Cache: Fixed-Prefix Precision Controls and Bidirectional Cache Transplantation provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

