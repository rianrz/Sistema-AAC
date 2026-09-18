# Sistema AAC - Gerador de Pranchas de Comunicação Alternativa

O **Sistema AAC** é uma solução em Java desenvolvida para a geração automática de pranchas de Comunicação Aumentativa e Alternativa (CAA/AAC). O sistema utiliza o modelo de linguagem `aac-board-generator-770m-ptbr-GGUF` através da biblioteca `ollama4j`, permitindo inferência local para a criação de pictogramas e vocabulário contextualizado[cite: 7, 9].

---

## 📋 Requisitos do Sistema

### Requisitos Funcionais (RF)
* **RF01 - Geração de Pranchas Contextuais:** Receber um pedido do utilizador (ex: "quero ir ao parque") e gerar uma prancha contendo ~12 itens relevantes[cite: 7].
* **RF02 - Categorização de Itens:** Formatar cada item no padrão `palavra|tipo` (onde tipo representa a classe gramatical/semântica)[cite: 7].
* **RF03 - Interface de Entrada:** Permitir a inserção de novos contextos/pedidos através do console Java (fase atual)[cite: 7].

### Requisitos Não-Funcionais (RNF)
* **RNF01 - Execução Local e Privacidade:** Processar a inferência 100% localmente via servidor Ollama (porta `11434`), sem dependência de APIs externas na nuvem[cite: 7].
* **RNF02 - Baixa Latência:** Utilizar um modelo leve de 770M de parâmetros quantizado em `Q4_K_M` para baixo consumo de memória e resposta rápida[cite: 7].
* **RNF03 - Determinismo:** Configurar a temperatura do modelo em `0.0` para garantir respostas consistentes e evitar alucinações de formato[cite: 7].
* **RNF04 - Integração Tipada:** Utilizar a biblioteca `ollama4j` v1.0.81 para a comunicação com a API REST do Ollama[cite: 7, 9].

---

## ⚖️ Tradeoffs e Decisões de Arquitetura

* **Modelo SLM (770M) vs. Modelo Genérico (7B+):**
  * **Escolha:** Modelo especializado de 770M de parâmetros[cite: 7].
  * **Tradeoff:** Menor capacidade conversacional genérica em troca de altíssima velocidade, menor consumo de RAM e foco estrito na geração de vocabulário AAC[cite: 7].
* **Execução Local vs. API Cloud:**
  * **Escolha:** Execução local via Ollama[cite: 7].
  * **Tradeoff:** Exige a instalação do ambiente Ollama na máquina do utilizador, mas elimina custos por token e garante total privacidade dos dados[cite: 7].
* **Prompting Estruturado:**
  * **Escolha:** Utilização explícita de tags `<start_of_turn>` e `<end_of_turn>` no prompt[cite: 7].
  * **Tradeoff:** Requer formatação rigorosa da string de envio, mas garante o retorno exclusivo da lista no formato `palavra|tipo` sem textos explicativos adicionais[cite: 7].

---

## 🛠️ Tecnologias e Dependências

* **Linguagem:** Java 25 (compatível com JDK 21+)
* **Gestor de Build:** Apache Maven
* **Integração LLM:** `ollama4j` (`v1.0.81`)[cite: 9]
* **Modelo IA:** `tardellirs/aac-board-generator-770m-ptbr-GGUF:Q4_K_M`[cite: 7]
* **Logging:** `logback-classic` (`v1.4.1`)[cite: 9]
* **Produtividade:** Project Lombok (`v1.18.30`)[cite: 9]
* **Runtime de IA:** Ollama[cite: 7]

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
