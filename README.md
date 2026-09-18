# Sistema AAC - Gerador de Pranchas de Comunicação Alternativa

O **Sistema AAC** é uma solução desenvolvida em Java para a geração automática de pranchas de Comunicação Aumentativa e Alternativa (CAA/AAC). O sistema utiliza o modelo de linguagem leve e quantizado `aac-board-generator-770m-ptbr-GGUF` através da biblioteca `ollama4j`, permitindo inferência local para apoiar a criação rápida de pictogramas e vocabulário contextualizado.

---

## 📋 Requisitos do Sistema

### Requisitos Funcionais (RF)
* **RF01 - Geração de Pranchas Contextuais:** O sistema deve receber um pedido do utilizador (ex: "quero ir ao parque") e gerar uma prancha contendo ~12 itens concretos e relevantes para o contexto.
* **RF02 - Categorização de Itens:** Cada item gerado deve conter a palavra e a sua respetiva classificação semântica/gramatical no formato `palavra|tipo` (onde tipo representa: verbo, substantivo, adjetivo, etc.).
* **RF03 - Interface de Entrada via Consola:** O sistema deve permitir a introdução de novos contextos/pedidos através do terminal.

### Requisitos Não-Funcionais (RNF)
* **RNF01 - Execução Local e Privacidade:** A inferência do modelo deve ocorrer 100% localmente via servidor Ollama, garantindo o funcionamento offline e o respeito pela privacidade dos dados.
* **RNF02 - Baixa Latência e Eficiência Computacional:** Utilização de um modelo pequeno (770M de parâmetros) quantizado em `Q4_K_M` para permitir a execução em máquinas com recursos limitados de CPU/RAM.
* **RNF03 - Resposta Determinística:** A temperatura de inferência deve ser mantida em `0.0` para evitar alucinações e padronizar o formato das saídas.
* **RNF04 - Integração Tipada em Java:** Comunicação com o servidor de IA intermediada pela biblioteca `ollama4j`.

---

## ⚖️ Tradeoffs e Decisões de Arquitetura

* **Modelo Especializado (770M) vs. Modelo Genérico Grande (7B+):**
  * *Escolha:* Modelo SLM de 770M.
  * *Tradeoff:* Menor capacidade de conversação genérica em troca de altíssima velocidade de geração local, menor consumo de memória e foco estrito na estruturação de dados de CAA.
* **Execução via Ollama Local vs. API em Nuvem:**
  * *Escolha:* Servidor local na porta `11434`.
  * *Tradeoff:* Exige a instalação do ambiente Ollama na máquina do utilizador, mas elimina custos por token e garante total privacidade dos dados.
* **Prompting Estruturado:**
  * *Escolha:* Utilização explícita de tags `<start_of_turn>` e `<end_of_turn>` no prompt.
  * *Tradeoff:* Requer formatação rigorosa da string de envio, mas garante o retorno exclusivo da lista no formato `palavra|tipo` sem textos explicativos adicionais.

---

## 🛠️ Tecnologias e Dependências

* **Linguagem:** Java 25 (compatível com JDK 21+)
* **Gestor de Build:** Apache Maven
* **Integração LLM:** `ollama4j` (v1.0.81)
* **Modelo IA:** `tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M`
* **Logging:** `logback-classic` (v1.4.1)
* **Produtividade:** Project Lombok (v1.18.30)
* **Runtime de IA:** Ollama

---

## 📁 Estrutura do Projeto

```text
SistemaACC/
├── src/
│   └── main/
│       └── java/
│           └── org/
│               └── example/
│                   ├── ACCBoard.java    # Lógica de inferência e integração com o Ollama
│                   └── Main.java        # Ponto de entrada do sistema
├── pom.xml                              # Gerenciamento de dependências Maven
└── README.md
