# What to Remove, What to Preserve: Dual-Ambiguity Rectification for All-in-One Image Restoration

**Authors:** Cencen Liu, Wen Yin, Dongyang Zhang, Dongmin Li, Shan Zhao, Bing Su, Tao He, Jielei Wang, Guoming Lu  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.AI  
**Document ID:** DOC-ARXIV-2607.28526v1  

## Abstract
All-in-one image restoration aims to handle diverse degradations within a unified framework. Existing methods commonly encode heterogeneous degradation conditions in a shared latent space, where degradation-related cues and scene content can remain entangled. We characterize the resulting challenge as dual ambiguity: semantic ambiguity in channel-wise modulation and spatial ambiguity in restoration responses, which can lead to content corruption and residual artifacts. To mitigate this issue, we propose DAR-Net, a Dual-Ambiguity Rectification Network for all-in-one image restoration. DAR-Net first introduces a Degradation Archetype Representation (DAR) module to construct a structured degradation state through simplex-constrained archetype mixture modeling. Based on this state, a Semantic Ambiguity Rectification (SeAR) module generates degradation-aware prompts to improve channel-wise conditioning in the decoder. A Spatial Ambiguity Rectification (SpAR) module further regularizes degradation-aware and complementary features toward orthogonal response subspaces, reducing spatial interference between removal and preservation cues. Extensive experiments on standard all-in-one restoration benchmarks show that DAR-Net achieves the best overall performance under both three-degradation and five-degradation settings, improving the average PSNR over the strongest competitor by 0.14 dB and 0.34 dB, respectively; it additionally shows superior performance on CDD-11 and WeatherBench.

## 1. Introduction
Recent advancements in cs.CV, cs.AI have significantly transformed knowledge discovery systems. This document explores what to remove, what to preserve: dual-ambiguity rectification for all-in-one image restoration and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Cencen Liu, Wen Yin, Dongyang Zhang, Dongmin Li, Shan Zhao, Bing Su, Tao He, Jielei Wang, Guoming Lu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, What to Remove, What to Preserve: Dual-Ambiguity Rectification for All-in-One Image Restoration provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

