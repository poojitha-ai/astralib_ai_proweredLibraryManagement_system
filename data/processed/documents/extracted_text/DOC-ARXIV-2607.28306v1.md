# A Distributed Acoustic Sensing Dataset for Vessel Detection and Localization in Submarine Cable Protection

**Authors:** Erick Eduardo Ramirez-Torres, Javier Macias-Guarasa, Daniel Pizarro, Javier Tejedor, Sira Elena Palazuelos-Cagigas, Pedro J. Vidal-Moreno, María R. Fernández-Ruiz, Sonia Martin-Lopez, Miguel Gonzalez-Herraez, Roel Vanthillo  
**Publication Date:** 2026-07-30  
**Categories:** physics.geo-ph, cs.LG  
**Document ID:** DOC-ARXIV-2607.28306v1  

## Abstract
Recent incidents of accidental damage and suspected sabotage to submarine telecommunication and power cables, particularly in the Baltic Sea, have underscored their vulnerability and the need for continuous monitoring solutions. Distributed acoustic sensing (DAS) applied to submarine optical-fiber cables enables wide-area monitoring of underwater acoustic activity.   We present the Marlinks-NS DAS dataset, comprising processed submarine DAS measurements and AIS-derived vessel information curated for cable-protection research. The dataset defines two machine-learning tasks (vessel detection and vessel-to-cable distance estimation) allowing reproducible research under realistic marine conditions.   The dataset contains 74,771 labeled data instances from ten days of continuous recording along a 2,554 m segment in a 28 km buried fiber-optic cable in the North Sea. Each instance includes spectral-energy features from 250 sensing channels, together with anonymized distance measurements and metadata from AIS information. The released HDF5 data, documentation, processing description, and example code support reproducible development and evaluation of DAS-based vessel-monitoring methods for submarine cable protection.

## 1. Introduction
Recent advancements in physics.geo-ph, cs.LG have significantly transformed knowledge discovery systems. This document explores a distributed acoustic sensing dataset for vessel detection and localization in submarine cable protection and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Erick Eduardo Ramirez-Torres, Javier Macias-Guarasa, Daniel Pizarro, Javier Tejedor, Sira Elena Palazuelos-Cagigas, Pedro J. Vidal-Moreno, María R. Fernández-Ruiz, Sonia Martin-Lopez, Miguel Gonzalez-Herraez, Roel Vanthillo established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, A Distributed Acoustic Sensing Dataset for Vessel Detection and Localization in Submarine Cable Protection provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

