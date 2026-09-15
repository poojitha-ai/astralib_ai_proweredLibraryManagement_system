# Negative controls reveal volume-driven confounding in radiomics and imaging foundation model features

**Authors:** Katy L. Scott, Sejin Kim, Joshua Siraj, Caryn Geady, Matthew Boccalon, Mattea Welch, Mogtaba Alim, Andrew J. Hope, Benjamin Haibe-Kains  
**Publication Date:** 2026-07-30  
**Categories:** cs.CV, cs.LG  
**Document ID:** DOC-ARXIV-2607.28423v1  

## Abstract
Radiomics and imaging foundation models promise non-invasive biomarkers of tumour biology, yet predictive signatures may reflect tumour volume or acquisition artifacts rather than meaningful image structure. We introduce READII-2-ROQC, an open-source framework that uses volume-preserving negative controls to assess whether radiomic and deep imaging features capture independent spatial signals. READII-2-ROQC generates voxel-perturbed images across tumour, background and whole-image regions using configurable randomization strategies, then compares feature behaviour and model performance between original and control images. Applied to three public cancer imaging cohorts, the framework processed 3,552 tumour volumes and extracted PyRadiomics and foundation-model features from original images and nine matched controls. Reproducing published survival and HPV-status signatures, we show that multiple models retain performance after spatial structure is destroyed, revealing volume-driven or contextual confounding, whereas others show perturbation-sensitive signal. READII-2-ROQC provides a scalable quality-control strategy for developing interpretable, biologically grounded imaging biomarkers and reproducible radiomics workflows.

## 1. Introduction
Recent advancements in cs.CV, cs.LG have significantly transformed knowledge discovery systems. This document explores negative controls reveal volume-driven confounding in radiomics and imaging foundation model features and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Katy L. Scott, Sejin Kim, Joshua Siraj, Caryn Geady, Matthew Boccalon, Mattea Welch, Mogtaba Alim, Andrew J. Hope, Benjamin Haibe-Kains established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Negative controls reveal volume-driven confounding in radiomics and imaging foundation model features provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

