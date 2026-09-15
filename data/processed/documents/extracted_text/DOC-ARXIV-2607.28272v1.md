# MemHarness: Memory Is Reconstructed, Not Replayed

**Authors:** Rong Wu, Daocheng Fu, Licheng Wen, Xuemeng Yang, Shu Zou, Jianbiao Mei, Yuxin Wang, Hairong Zhang, Yu Yang, Tao Hu, Cong Zhang, Botian Shi, Pinlong Cai  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28272v1  

## Abstract
Retrieving past experiences has become a common strategy to enhance large language model agents. However, most existing memory-augmented agents treat retrieved experiences as static records to be replayed verbatim, injecting them into the context regardless of whether they align with the agent's current situation. This ``replay'' paradigm ignores the gap between the abstract, general nature of stored experience and the concrete, ever-changing states encountered at decision time, frequently causing negative transfer. In contrast, humans rarely recall past experiences verbatim; instead, they reorganize and adapt retrieved memories to fit the present context. Inspired by this, we propose MemHarness, a framework that equips LLM agents to actively harness and reconstruct past experiences based on the present context. At each decision step, a unified policy model critiques and reconstructs the retrieved experience conditioned on the current state, producing context-grounded guidance before acting. This reconstructive ability emerges naturally through end-to-end training with GRPO. Experiments on ALFWorld and WebShop show that MemHarness substantially outperforms pure RL and static memory-augmented baselines, demonstrating strong robustness in out-of-distribution (OOD) scenarios. Furthermore, our analyses reveal that this reconstruction objective not only prevents negative transfer but also serves as latent guidance during training, fundamentally improving the agent's intrinsic reasoning capabilities.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores memharness: memory is reconstructed, not replayed and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Rong Wu, Daocheng Fu, Licheng Wen, Xuemeng Yang, Shu Zou, Jianbiao Mei, Yuxin Wang, Hairong Zhang, Yu Yang, Tao Hu, Cong Zhang, Botian Shi, Pinlong Cai established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, MemHarness: Memory Is Reconstructed, Not Replayed provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

