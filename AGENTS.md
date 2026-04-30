# AGENTS.md

## Purpose

This file defines the engineering rules, architecture boundaries, coding conventions, and implementation guardrails for building the Loan Origination System (LOS) as a **Spring Boot modular monolith** integrated with **Apache Fineract (LMS)**.

The system is designed as:

React Frontend → Mini-LOS (Spring Boot) → Fineract (LMS)

Use this document as the operating guide for all code generation, refactoring, and design decisions.

---

## Product Goal

Build a **multi-tenant Loan Origination System (LOS)** that:

- manages applications, KYC, credit checks, documents, tasks
- integrates with Apache Fineract as the **loan accounting engine**
- supports configurable workflows and automation
- supports role-based access control (RBAC)
- supports multi-tenant SaaS usage
- can evolve into microservices later

---

## Core Architecture (Critical)

### 1. System Roles

Mini-LOS = Orchestration + Business Logic + UI API  
Fineract = Loan accounting + repayment + schedules  
Frontend = Presentation only  

---

### 2. Non-negotiable boundary

Frontend MUST NOT call Fineract directly  

All communication must go through Mini-LOS.

---

### 3. Adapter pattern is mandatory

Mini-LOS acts as:

Business API → translates → Fineract API  

Frontend sends:

{ "productCode": "PHONE_12_WEEKS" }

Mini-LOS translates:

{ "productId": 7 }

---

## Multi-Tenant Design

### Tenant model

tenants (
  id UUID PRIMARY KEY,
  code VARCHAR UNIQUE,
  name VARCHAR,
  fineract_tenant_id VARCHAR,
  active BOOLEAN
);

---

### Tenant rules

- tenant_id is created ONCE during onboarding
- tenant_id is stored on all business tables
- tenant_id is NEVER passed from frontend
- tenant_id is ALWAYS derived from authenticated user

---

### Request lifecycle

Login → JWT contains tenant_id  
→ Backend sets TenantContext  
→ All DB queries use tenant_id  
→ Fineract calls use fineract_tenant_id  

---

### Strict rule

Every query MUST include tenant_id  

---

## Authentication & Authorization (RBAC)

### Model

users → user_roles → roles → role_permissions → permissions  

---

### Permissions (example)

| Role                   | Permissions                                                                                                                                          |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LOAN_OFFICER**       | `CLIENT_VIEW`, `CLIENT_CREATE`, `LEAD_VIEW`, `LEAD_CREATE`, `LOAN_VIEW`, `LOAN_CREATE`, `DEVICE_ASSIGN`, `TASK_VIEW`, `TASK_CREATE`, `TASK_COMPLETE` |
| **KYC_OFFICER**        | `CLIENT_VIEW`, `LEAD_VIEW`, `KYC_APPROVE`, `KYC_REVOKE`, `TASK_VIEW`, `TASK_COMPLETE`                                                                |
| **CREDIT_OFFICER**     | `CLIENT_VIEW`, `LOAN_VIEW`, `CREDIT_CHECK_APPROVE`, `CREDIT_CHECK_REVOKE`, `TASK_VIEW`, `TASK_COMPLETE`, `TASK_REASSIGN`                             |
| **OPERATIONS_MANAGER** | all loan officer, KYC, credit, task permissions + `DEVICE_VIEW`, `DEVICE_CREATE`, `DEVICE_UPDATE`, `TASK_REASSIGN`                                   |
| **PRODUCT_ADMIN**      | `LOAN_PRODUCT_VIEW`, `LOAN_PRODUCT_CREATE`, `LOAN_PRODUCT_UPDATE`, `GL_ACCOUNT_VIEW`, `GL_ACCOUNTING_RULE_VIEW`                                      |
| **INVENTORY_ADMIN**    | `DEVICE_VIEW`, `DEVICE_CREATE`, `DEVICE_UPDATE`, `DEVICE_ASSIGN`                                                                                     |
| **ACCOUNTING_ADMIN**   | `GL_ACCOUNT_VIEW`, `GL_ACCOUNT_CREATE`, `GL_ACCOUNT_UPDATE`, `GL_ACCOUNTING_RULE_VIEW`, `GL_ACCOUNTING_RULE_CREATE`, `GL_ACCOUNTING_RULE_UPDATE`     |
| **USER_ADMIN**         | `USER_VIEW`, `USER_CREATE`, `USER_UPDATE`, `USER_ASSIGN_OFFICE`, `USER_ASSIGN_ROLES`, `ROLE_VIEW`                                                    |
| **ROLE_ADMIN**         | `ROLE_VIEW`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_ASSIGN_PERMISSIONS`                                                                                 |
| **VIEWER**             | all `*_VIEW` permissions only                                                                                                                        |
| **ADMIN**              | all permissions                                                                                                                                      |
| Role                   | Permissions                                                                                                                                          |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| **LOAN_OFFICER**       | `CLIENT_VIEW`, `CLIENT_CREATE`, `LEAD_VIEW`, `LEAD_CREATE`, `LOAN_VIEW`, `LOAN_CREATE`, `DEVICE_ASSIGN`, `TASK_VIEW`, `TASK_CREATE`, `TASK_COMPLETE` |
| **KYC_OFFICER**        | `CLIENT_VIEW`, `LEAD_VIEW`, `KYC_APPROVE`, `KYC_REVOKE`, `TASK_VIEW`, `TASK_COMPLETE`                                                                |
| **CREDIT_OFFICER**     | `CLIENT_VIEW`, `LOAN_VIEW`, `CREDIT_CHECK_APPROVE`, `CREDIT_CHECK_REVOKE`, `TASK_VIEW`, `TASK_COMPLETE`, `TASK_REASSIGN`                             |
| **OPERATIONS_MANAGER** | all loan officer, KYC, credit, task permissions + `DEVICE_VIEW`, `DEVICE_CREATE`, `DEVICE_UPDATE`, `TASK_REASSIGN`                                   |
| **PRODUCT_ADMIN**      | `LOAN_PRODUCT_VIEW`, `LOAN_PRODUCT_CREATE`, `LOAN_PRODUCT_UPDATE`, `GL_ACCOUNT_VIEW`, `GL_ACCOUNTING_RULE_VIEW`                                      |
| **INVENTORY_ADMIN**    | `DEVICE_VIEW`, `DEVICE_CREATE`, `DEVICE_UPDATE`, `DEVICE_ASSIGN`                                                                                     |
| **ACCOUNTING_ADMIN**   | `GL_ACCOUNT_VIEW`, `GL_ACCOUNT_CREATE`, `GL_ACCOUNT_UPDATE`, `GL_ACCOUNTING_RULE_VIEW`, `GL_ACCOUNTING_RULE_CREATE`, `GL_ACCOUNTING_RULE_UPDATE`     |
| **USER_ADMIN**         | `USER_VIEW`, `USER_CREATE`, `USER_UPDATE`, `USER_ASSIGN_OFFICE`, `USER_ASSIGN_ROLES`, `ROLE_VIEW`                                                    |
| **ROLE_ADMIN**         | `ROLE_VIEW`, `ROLE_CREATE`, `ROLE_UPDATE`, `ROLE_ASSIGN_PERMISSIONS`                                                                                 |
| **VIEWER**             | all `*_VIEW` permissions only                                                                                                                        |
| **ADMIN**              | all permissions                                                                                                                                      |


---

### Enforcement

Use Spring Security:

@PreAuthorize("hasAuthority('CLIENT_CREATE')")

AND service-level validation:

if (!ownsRecord && !hasPermission("VIEW_ALL")) { throw new AccessDeniedException(); }

---

### Ownership rule

Users can only access records where:
- tenant_id matches
- AND ownership rules pass

---

## Data Ownership Rules

### Mini-LOS owns

applications  
clients (mapping to fineractClientId)  
KYC  
credit checks  
documents  
tasks  
inventory/devices  
workflow state  
automation  
users/roles  

---

### Fineract owns

loan products (core config)  
loans  
repayment schedules  
accounting  
GL rules  
charges  

---

### Critical rule

Do NOT duplicate Fineract configuration locally  

Only store:

mapping IDs (productId, clientId, etc.)

---

## Mapping Layer

loan_product_mapping (
  id,
  tenant_id,
  code,
  display_name,
  fineract_product_id
);

---

### Rule

Store IDs → YES  
Store full Fineract config → NO  

---

## Async Processing Model

### Client creation flow

1. POST /applications  
2. Save application (status = PENDING_CLIENT_CREATION)  
3. Return response  

4. Background job:
   - create client in Fineract  
   - save fineractClientId  
   - call KYC provider  
   - update status  

---

### Status model

PENDING_CLIENT_CREATION  
CLIENT_CREATED  
KYC_IN_PROGRESS  
KYC_PASSED  
KYC_FAILED  
READY_FOR_LOAN  

---

### Rule

Do NOT block HTTP requests for external calls  

---

## Integration Rules

Use gateways:

KycGateway  
CreditCheckGateway  
FineractGateway  
InventoryGateway  

---

## Database Rules (MariaDB)

- Use MariaDB  
- Use Flyway  
- No manual schema changes  

---

## Security Rules

NEVER accept tenant_id from frontend  

Always derive from authenticated user  

---

## Loan Product Handling

Read → from Mini-LOS DB  
Create → Mini-LOS → Fineract → save mapping  

---

## Anti-Patterns

- frontend calling Fineract  
- duplicating Fineract configs  
- missing tenant filters  
- business logic in controllers  
- blocking external calls  

---

## Final Instruction

- enforce tenant isolation  
- enforce RBAC  
- abstract Fineract  
- keep frontend unaware of Fineract internals  
- use async processing  
- design for SaaS  
