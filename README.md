# BiniTech Fiscal

Serviço de emissão de **NFC-e** do BiniTech PDV, falando **direto com a SEFAZ**, sem intermediário. Cada loja assina as próprias notas com o **certificado digital A1 dela**.

O XML, a assinatura XMLDSig e os webservices SOAP de cada estado usam a biblioteca open source [Java_NFe](https://github.com/Samuel-Oliveira/Java_NFe) (MIT). O resto é deste serviço: fila, retentativas, contingência, numeração, armazenamento seguro do certificado, cupom em PDF e API.

## Como funciona

```
PDV ──(RabbitMQ: sale.completed)──▶ fila fiscal.sales.completed ──▶ registra a venda
                                                                    │
Frontend ──(REST /api/fiscal/**)──▶ emitir / consultar / cancelar   ▼
                                                    máquina de estados da NFC-e ──▶ SEFAZ (SOAP + A1)
```

1. Ao finalizar uma venda, o PDV publica `sale.completed` no exchange `binitech.sales`, com itens, NCM/CFOP/CSOSN, pagamentos e CPF opcional.
2. Se a loja tiver emitente configurado, o serviço grava um documento fiscal por venda (índice único `tenantId + saleId`, então mensagem repetida não duplica nota).
3. Com **emissão automática**, a nota vai para `PENDING` e é processada na hora. Sem emissão automática, fica em `AWAITING_REQUEST` até o caixa pedir pela API, que permite informar o CPF.
4. A máquina de estados numera, monta, assina, valida contra os XSDs oficiais e envia para a SEFAZ.

### Resiliência

| Situação | O que o serviço faz |
|---|---|
| Fila ou serviço fiscal fora do ar | A mensagem fica no RabbitMQ (fila durável). Erros são reprocessados 6 vezes com espera crescente e depois vão para `fiscal.sales.completed.dead`. |
| Venda repetida na fila | Índice único por venda: a segunda mensagem é ignorada. |
| Vários workers ou instâncias | Cada documento é reservado com `findAndModify` e um prazo (`lockedUntil`). Gravações usam trava otimista (`version`). |
| Numeração | Contador atômico por loja, ambiente e série. O número é gravado **antes** do envio e reaproveitado nas retentativas, então nunca há duas chaves para o mesmo número. |
| Timeout **depois** de enviar | A nota fica marcada como "entrega incerta" e a próxima tentativa **consulta a chave** na SEFAZ antes de reenviar, evitando duplicidade. |
| Duplicidade (204/539) | Consulta a chave e recupera o protocolo da nota já autorizada. |
| SEFAZ fora do ar **antes** do envio (conexão recusada, 108/109) | Retenta com espera crescente (2 s, 5 s, 15 s, 30 s, 1 min…). Depois de 15 s ou 3 tentativas, se a loja permitir, emite em **contingência offline** (`tpEmis=9`, QR Code v3 assinado com o certificado). O cupom pode ser impresso na hora e a nota é transmitida quando a SEFAZ voltar. |
| Rejeição 704 (emissão atrasada) | Remonta o XML com o horário atual e reenvia sozinho. |
| Rejeição por dados (NCM, schema etc.) | Guarda `cStat`/motivo. O caixa corrige e pede de novo reaproveitando o número. Se ninguém corrigir em 24 h, o número é **inutilizado** automaticamente. |
| Certificado vencido ou loja incompleta | Adia sem consumir número. |

### Segurança

- O arquivo `.pfx` e a senha do A1 ficam **criptografados com AES-256-GCM** (`FISCAL_MASTER_KEY`) e nunca são devolvidos pela API.
- Ao enviar o certificado, o serviço confere a validade e se o CNPJ dele é o da loja.
- As requisições usam a sessão do BiniTech Auth (`/api/auth/session`). Dados fiscais, certificado e cancelamento exigem administrador da loja.
- Cada chamada à SEFAZ usa o próprio cliente HTTPS do certificado da loja (`modoMultithreading`), sem estado global entre lojas.

## Escopo atual

- NFC-e para **Simples Nacional (CRT 1)** e **MEI (CRT 4)**, com CSOSN 102, 103, 300, 400 e 500. PIS/COFINS saem como CST 49.
- QR Code **v3** (NT 2025.001), sem CSC na emissão on-line.
- Responsável técnico (`infRespTec`) opcional, pelas variáveis `FISCAL_RESP_TEC_*`.
- Regime normal (CST, alíquotas, IBS/CBS) ainda não é suportado.

## API

| Método | Rota | Descrição |
|---|---|---|
| GET/PUT | `/api/fiscal/issuer` | Dados fiscais da loja |
| POST | `/api/fiscal/issuer/certificate` | Upload do A1 (multipart `file` + `password`) |
| GET | `/api/fiscal/issuer/sefaz-status` | Status do serviço da SEFAZ com o certificado da loja |
| GET | `/api/fiscal/cep/{cep}` | Endereço e código IBGE (ViaCEP) |
| GET | `/api/fiscal/nfce/{saleId}` | Situação da NFC-e da venda |
| POST | `/api/fiscal/nfce/{saleId}/issue` | Emite (ou reemite após rejeição), com `consumerCpf` opcional |
| POST | `/api/fiscal/nfce/{saleId}/cancel` | Cancela (motivo de 15 a 255 caracteres) |
| GET | `/api/fiscal/nfce/{saleId}/danfe` | Cupom NFC-e 80 mm em PDF |

## Variáveis de ambiente

| Variável | Descrição | Padrão |
|---|---|---|
| `FISCAL_MONGODB_URI` | MongoDB | — |
| `FISCAL_MONGODB_DATABASE` | Banco | `binitech_fiscal` |
| `FISCAL_MASTER_KEY` | Chave AES-256 em Base64 (32 bytes) para os certificados | — |
| `FISCAL_ENVIRONMENT` | `2` homologação, `1` produção | `2` |
| `FISCAL_SEFAZ_TIMEOUT_MS` | Timeout das chamadas à SEFAZ | `20000` |
| `FISCAL_RESP_TEC_CNPJ` / `_CONTACT` / `_EMAIL` / `_PHONE` | Responsável técnico (opcional) | — |
| `AUTH_SERVICE_URL` | BiniTech Auth | `http://localhost:8081` |
| `RABBITMQ_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | RabbitMQ compartilhado com o PDV | `localhost` |
| `CORS_ALLOWED_ORIGINS` | Origens do frontend | `http://localhost:4200` |

## Testes

```bash
mvn verify
```

- **NfceSigningTest:** gera um certificado A1 de teste e monta, **assina e valida contra os XSDs oficiais** uma NFC-e normal e uma em contingência offline, sem rede.
- **DanfeRenderingTest:** gera o cupom em PDF a partir do `nfeProc`.
- **NfceProcessorTest:** máquina de estados (autorização, timeout ambíguo, consulta antes de reenviar, contingência, duplicidade, rejeição 704, inutilização).
- **MongoRepositoriesTest:** Mongo embarcado, reserva concorrente exclusiva, trava otimista, numeração sem repetição com 100 chamadas paralelas.
