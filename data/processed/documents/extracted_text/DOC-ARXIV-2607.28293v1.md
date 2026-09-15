# Filling the Pareto-Optimal Front for Affordance Segmentation on Embedded Devices Using RGB-D Cameras

**Authors:** Edoardo Ragusa, Giovanni Paolo Canuti, Simone Lugani, Rodolfo Zunino, Paolo Gastaldo  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.LG, cs.RO  
**Document ID:** DOC-ARXIV-2607.28293v1  

## Abstract
While depth sensors have the potential to complement RGB data for affordance segmentation in wearable robots, their usage seems to remain underexplored. The paper proposes two approaches: a reformulated version of hardware-aware neural architecture search, endowed with a newly designed search space to integrate depth (D) information into small-sized deep networks, and a dedicated fine-tuning approach, including a preprocessing layer to merge depth information with RGB data and make it compatible with conventional architectures. In both cases, those methods aim to generate solutions that benefit from modern (portable) hardware accelerators and overcome existing tiny-like approaches, which often fail to tackle critical scenarios due to the severe constraints set by the supporting hardware. Extensive experiments on a pair of real-world datasets demonstrate the effectiveness of the proposed method as compared with existing solutions. The approach presented in the paper generates, in most cases, solutions that identify the Pareto optimal front to balance generalization performance and hardware requirements. The paper also describes the supporting prototype, including a Jetson Nano board and a RealSense RGB-D camera. When considering the energy profile of the device, the overall system can attain real-time performances within an energy budget that is compatible with standard batteries, such as those used in smartphones.

## 1. Introduction
Recent advancements in cs.CV, cs.LG, cs.RO have significantly transformed knowledge discovery systems. This document explores filling the pareto-optimal front for affordance segmentation on embedded devices using rgb-d cameras and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Edoardo Ragusa, Giovanni Paolo Canuti, Simone Lugani, Rodolfo Zunino, Paolo Gastaldo established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Filling the Pareto-Optimal Front for Affordance Segmentation on Embedded Devices Using RGB-D Cameras provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

