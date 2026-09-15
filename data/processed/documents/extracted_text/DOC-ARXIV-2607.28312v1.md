# ObjectStream: Latent Objects as Memory Anchors for Streaming Video Understanding

**Authors:** Mingkang Dong, Muxin Pu, Jie Li, Bohan Guo, Songruo Chen, Bin Ren, Xu Zheng, Chen Zhao, Tianwen Qian, Mohamed Elhoseiny, Yuqian Fu  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.AI  
**Document ID:** DOC-ARXIV-2607.28312v1  

## Abstract
Streaming video understanding requires models to continuously retain useful visual evidence before future questions are known. Existing approaches primarily manage the growing visual context according to token importance, temporal redundancy, or segment-level relevance, but rarely organize evidence around objects that persist and evolve over time. Thus, in this paper, we introduce ObjectStream, a training-free framework that treats latent objects as memory anchors for streaming video understanding. ObjectStream induces spatially coherent latent objects directly from frozen Video-LLM representations, links them across frames into persistent anchors, and maintains their histories under a bounded memory budget, without requiring external object detectors or segmentation models. Built on these anchors, ObjectStream preserves three complementary forms of evidence: persistent object histories, transient object changes, and recent visual context. This design enables existing Video Large Language Models (Video-LLMs) to reason over object identities, interactions, and state changes while leaving the underlying model unchanged. Extensive experiments on online streaming and offline long-video benchmarks demonstrate both effectiveness and efficiency. In online streaming evaluation, ObjectStream improves Qwen2.5-VL-7B by 10.0 points on OVO-Bench Real-Time Visual Perception, while reducing peak GPU mem-ory and TTFT by approximately 50%. On offline long-video benchmarks, it surpasses the full-token baseline while discarding 82.5% of visual tokens. These results highlight latent objects as a practical and effective organizing principle for compact streaming video memory.

## 1. Introduction
Recent advancements in cs.CV, cs.AI have significantly transformed knowledge discovery systems. This document explores objectstream: latent objects as memory anchors for streaming video understanding and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Mingkang Dong, Muxin Pu, Jie Li, Bohan Guo, Songruo Chen, Bin Ren, Xu Zheng, Chen Zhao, Tianwen Qian, Mohamed Elhoseiny, Yuqian Fu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, ObjectStream: Latent Objects as Memory Anchors for Streaming Video Understanding provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

