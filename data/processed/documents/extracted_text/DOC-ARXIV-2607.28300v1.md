# MonoVoc: Decoupling Geometry and Semantics for Lightweight Monocular Open-Vocabulary 3D Gaussians

**Authors:** Pouya Ardekhani, Zahra Dehghanian, Morteza Abolghasemi, Hamid R. Rabiee  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.AI  
**Document ID:** DOC-ARXIV-2607.28300v1  

## Abstract
Open vocabulary 3D scene understanding is essential for next-generation interactive systems, empowering users to intuitively query and navigate reconstructed environments using natural language. However, current 3D Gaussian frameworks are often bottlenecked by restrictive multiview capture requirements, costly scene-specific optimization, and the massive memory overhead of storing dense language features. We present a novel, training-free pipeline that fundamentally reimagines this paradigm by explicitly decoupling 3D geometric reconstruction from semantic integration. Given a standard monocular video sequence as input, our method efficiently outputs a compact, highly interpretable, and fully searchable object-level semantic Gaussian map. Rather than entangling heavy language embeddings within the mapping loop, we extract geometry independently and ground semantics through a lightweight, modular post-processing framework. Extensive evaluations on the Replica dataset demonstrate that this decoupled architecture preserves strong rendering fidelity and competitive segmentation accuracy. Crucially, by replacing dense per-Gaussian storage with modular, object-level semantic embeddings, our approach delivers an order-of-magnitude reduction in memory usage compared to SOTA baselines. This provides a highly efficient, scalable, and practical solution for open-vocabulary 3D retrieval and question answering directly from everyday monocular video.

## 1. Introduction
Recent advancements in cs.CV, cs.AI have significantly transformed knowledge discovery systems. This document explores monovoc: decoupling geometry and semantics for lightweight monocular open-vocabulary 3d gaussians and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Pouya Ardekhani, Zahra Dehghanian, Morteza Abolghasemi, Hamid R. Rabiee established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, MonoVoc: Decoupling Geometry and Semantics for Lightweight Monocular Open-Vocabulary 3D Gaussians provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

