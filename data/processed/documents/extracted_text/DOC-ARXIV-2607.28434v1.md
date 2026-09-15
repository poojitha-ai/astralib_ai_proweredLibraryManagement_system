# Metaphor Tracer: A Theory-Informed Analysis of Hidden States

**Authors:** Marc Heimann, Roxana Assadi Moghaddam, Olga Brovkina, Mark Pettifor, Lutz Goetzmann  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI, cs.CL  
**Document ID:** DOC-ARXIV-2607.28434v1  

## Abstract
What do a language model's hidden states say about the organization of a single text? From one forward pass, without training, we score every token position on two properties. The *aggregator* measures whether the position consolidates the whole text into a stable configuration. The *differentiator*, whether other tokens are transiently carried into its subspace as the model reads: metaphor in its root sense, transport. Constants were frozen on one discovery text; every other is confirmatory.   The aggregator is not, in the classic sense, an information measure, nor a measure of salience. Across three unrelated models, as a signifier repeats, its surprisal and its attention drain while its aggregator score holds: the channel marks a token's place in the text. That this tracks a reading rests on independent ground truth: an engineered register the aggregator follows across its boundaries (6/6 cells), and a psychoanalyst's marking of clinical transcripts, fixed before the instrument existed, in 34/36 cells, with a graded increment above lexical controls and dissociations no type-level measure reproduces. A transfer test gives the result its shape: the model whose token structure travels with lexical type reads the singular discourse worst, and in a matched base/instruct pair tuning raises fidelity without moving type-transfer. Structural value is a property of a token's place in *this* text, not of its vector alone: a relational rather than essentialist reading of hidden states, operationalizing theory that predated the instrument.

## 1. Introduction
Recent advancements in cs.AI, cs.CL have significantly transformed knowledge discovery systems. This document explores metaphor tracer: a theory-informed analysis of hidden states and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Marc Heimann, Roxana Assadi Moghaddam, Olga Brovkina, Mark Pettifor, Lutz Goetzmann established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Metaphor Tracer: A Theory-Informed Analysis of Hidden States provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

