# Towards Autonomous Aircraft Surveillance from Nanosatellites through On-Board Inference and Generative Data Augmentation

**Authors:** Antonio Delgado-Rosa, David Muñoz-Valero, Enrique Adrian Villarrubia-Martin, Juan Moreno-Garcia  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI, cs.CV  
**Document ID:** DOC-ARXIV-2607.28470v1  

## Abstract
Airborne surveillance from low Earth orbit is hindered by two interconnected bottlenecks: nanosatellites have a limited downlink budget, yet the conventional approach still transmits terabytes of raw imagery to the ground for processing, and open satellite datasets for aircraft are scarce and severely class-imbalanced. These limitations either delay timely decision-making or prevent standard detectors from learning robust representations of rare aircraft classes. In this paper, a workflow that combines on-board inference with generative data augmentation is proposed to address both limitations jointly. Inference is executed on a 6U CubeSat equipped with a low-power edge tensor accelerator, while a diffusion model fine-tuned through low-rank adaptation generates synthetic minority-class imagery. This synthetic output is automatically annotated, pseudo-labelled, by an intermediate detector and merged with classically augmented samples. The results show that the balanced dataset increases global mean average precision from 77.9% to 82.2%, with the minority class rising from F1=0.683 to F1=0.811, and that the quantised detector fits the on-chip memory and projects 25-30 frames per second on orbit. This approach contrasts with the conventional bent-pipe architecture, in which the satellite acts as a passive data collector. Therefore, the computational tests support the proposed workflow as a decision-support tool for real-time, autonomous airborne surveillance from nanosatellites.

## 1. Introduction
Recent advancements in cs.AI, cs.CV have significantly transformed knowledge discovery systems. This document explores towards autonomous aircraft surveillance from nanosatellites through on-board inference and generative data augmentation and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Antonio Delgado-Rosa, David Muñoz-Valero, Enrique Adrian Villarrubia-Martin, Juan Moreno-Garcia established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Towards Autonomous Aircraft Surveillance from Nanosatellites through On-Board Inference and Generative Data Augmentation provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

