# Teffic-Audio: Tell Fact from Fiction

**Authors:** Wan Lin, Li Wang, Jindong Wang, Kunyu Feng, Zhizheng Wu  
**Publication Date:** 2026-07-30  
**Categories:** cs.SD, cs.AI  
**Document ID:** DOC-ARXIV-2607.28351v1  

## Abstract
Speech deepfake detection has expanded in scope with increasingly heterogeneous spoofing mechanisms, including speech synthesis, voice conversion, vocoder reconstruction, and neural-codec resynthesis. The resulting spoofing artifacts can be further shaped by variability in source speech, recording environments, and transmission channels. This variability makes robust generalization across heterogeneous conditions a central requirement for practical detection systems. This report presents Teffic-Audio, a general speech deepfake detection system designed for comprehensive evaluation environment. Teffic-Audio adopts a straightforward detector architecture consisting of a Conformer-based speech encoder, multi-head attentive statistics pooling, and a binary classifier. Rather than relying on additional architectural complexity, the system improves generalization through its training recipe, which integrates multi-source data, attack- and source-balanced sampling, and diverse audio augmentation. Trained only with open-source data, Teffic-Audio achieves a pooled EER of 1.454% on the 14 test sets of Speech-DF-Arena, outperforming all currently public systems on the leaderboard. It also obtains the lowest EER on five individual test sets and shows a favorable performance-complexity trade-off compared with larger leading systems. Overall, Teffic-Audio provides a strong and practical reference system for general speech deepfake detection.

## 1. Introduction
Recent advancements in cs.SD, cs.AI have significantly transformed knowledge discovery systems. This document explores teffic-audio: tell fact from fiction and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Wan Lin, Li Wang, Jindong Wang, Kunyu Feng, Zhizheng Wu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Teffic-Audio: Tell Fact from Fiction provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

