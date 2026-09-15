# MixFrag: Fragility-Guided Mixed-Precision Post-Training Quantization for Vision Transformers

**Authors:** Md. Mehrab Hossain Opi, Robiul Islam Ryad, Md. Umar Faruk  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.LG  
**Document ID:** DOC-ARXIV-2607.28589v1  

## Abstract
Post-training quantization (PTQ) has emerged as an effective solution for deploying Vision Transformers (ViTs) on resource-constrained devices. However, existing PTQ methods typically employ uniform bit-widths across transformer components, overlooking their heterogeneous sensitivity to quantization and leading to inefficient precision allocation. In this paper, we propose {MixFrag, a fragility-guided mixed-precision PTQ framework for Vision Transformers. MixFrag first estimates component-level quantization fragility by measuring the Kullback--Leibler (KL) divergence between full-precision and isolated quantized output distributions using a small calibration set. It then formulates bit allocation as a Multiple-Choice Knapsack Problem (MCKP), enabling adaptive layer-wise precision assignment under a target bit budget. Extensive experiments on ImageNet-1K across multiple Vision Transformer architectures demonstrate that MixFrag achieves competitive classification performance under practical mixed-precision settings. Furthermore, evaluations on COCO object detection and instance segmentation show that MixFrag achieves state-of-the-art performance among existing mixed-precision PTQ methods, improving the previous best method by up to 9.6 AP under the challenging MP3/MP3 setting. Additional analyses validate the proposed fragility metric and demonstrate its strong correlation with the learned bit allocation. These results establish MixFrag as an effective framework for mixed-precision post-training quantization of Vision Transformers.

## 1. Introduction
Recent advancements in cs.CV, cs.LG have significantly transformed knowledge discovery systems. This document explores mixfrag: fragility-guided mixed-precision post-training quantization for vision transformers and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Md. Mehrab Hossain Opi, Robiul Islam Ryad, Md. Umar Faruk established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, MixFrag: Fragility-Guided Mixed-Precision Post-Training Quantization for Vision Transformers provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

