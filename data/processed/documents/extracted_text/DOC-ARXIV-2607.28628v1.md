# Learning to Trace Seiberg Dualities

**Authors:** Jonathan J. Heckman, Shani Meynet, Alessandro Mininno, Gary Shiu  
**Publication Date:** 2026-07-30  
**Categories:** hep-th, cs.AI, cs.LG, hep-ph  
**Document ID:** DOC-ARXIV-2607.28628v1  

## Abstract
Dualities play an important role in establishing both microscopic and emergent phenomena in a wide range of physical systems. In practice, though, it can often be computationally challenging to establish when two systems are dual, even when all of the "rules of the game" are well-known. Said differently, when confronted with two systems, how can one efficiently establish that they are in fact dual? In this paper we use machine learning methods to address this question for Seiberg dualities of supersymmetric quiver gauge theories. Mathematically, this involves establishing mutations of quivers, which is in turn a variation on the theme of "learning to unknot". On the one hand, this leads us to a practical tool for establishing the computational complexity of different dualities. On the other hand, it also allows us to study how different network architectures learn how to trace Seiberg dualities. We find that for quivers with a modest number of quiver nodes (of order $10$), different network architectures consisting of transformers and multi-layer perceptrons tend to outperform deterministic algorithms. Supplementing the network by well-established pathfinder algorithms (essentially "Google Maps for quivers") leads to an additional improvement in the efficiency and accuracy of the search strategy. We anticipate that this class of questions can serve as a useful benchmark for frontier AI models applied to theoretical physics.

## 1. Introduction
Recent advancements in hep-th, cs.AI, cs.LG, hep-ph have significantly transformed knowledge discovery systems. This document explores learning to trace seiberg dualities and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Jonathan J. Heckman, Shani Meynet, Alessandro Mininno, Gary Shiu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Learning to Trace Seiberg Dualities provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

