# MokaSE

MokaSE originated from a real organizational challenge that I encountered during my personal experience in the restaurant industry.

Many software solutions available on the market provide effective tools for managing sales operations, inventory,
and cash register activities. 
However, they often fail to fully address the specific requirements of workforce management in a restaurant environment. 
Staff scheduling in this sector requires particular attention not only to the skills needed during specific time slots, 
but also to employee requests and availability. This becomes especially important when dealing with on-call contracts 
and highly variable working schedules, circumstances that often make solutions designed for other industries too rigid 
to be truly effective.

The goal of MokaSE was to develop, following the requirements provided by my managers, 
a staff management and scheduling system capable of supporting the daily work of a restaurant manager while taking into account 
both the company's business rules and the individual constraints of its employees.

# Overview

MokaSE is a console-based application developed using Java 21.

The system allows a manager to create employee profiles, assign professional skills, define employee availability, manage leave requests, 
and automatically generate weekly work schedules.
Within MokaSE, schedule generation is not treated as a simple sequence of assignments. 
Instead, it is considered a decision-making process driven by multiple factors, including employee qualifications, 
contractual working hours, approved leave requests, and assignment priorities.

For this reason, the scheduling engine represents the operational core of the application and drives most of its business logic. 
The goal is not simply to fill shifts, but to produce a schedule that balances business requirements with employee constraints 
while remaining consistent with the organization's operational rules.

The project also includes a JSON-based persistence layer that allows application data to be stored and reloaded between executions 
without relying on an external database system.

# Architecture

The application follows a modular architecture in which responsibilities are separated according to their role within the system.

The main functional areas are:

Employee Management;
Leave Management;
Schedule Templates;
Schedule Generation;
Persistence;
Console User Interface;

Each area focuses on a specific responsibility and interacts with the others through well-defined boundaries. 
This separation allows business rules to remain independent from both presentation logic and persistence concerns, 
improving maintainability and reducing coupling between components.

Although MokaSE was originally developed as a standalone Java SE application, 
its internal organization was designed with future extensibility in mind. 
This architectural approach later made it possible to evolve the project into a complete web-based ecosystem composed of:

* MokaWeb Backend (Spring Boot)
* MokaWeb Frontend (React + TypeScript)

By keeping scheduling logic, domain concepts, and persistence responsibilities separated, 
the transition from a console application to a multi-user web platform required significantly fewer architectural changes 
than a monolithic design would have required.

Executive Architecture Diagram:

`docs/Moka-MokaSE_Domain&Scheduling_Architecture.drawio.png`


# Employee Domain

The Employee domain represents the central business entity of the application.
Each employee contains all the information required by the scheduling engine to perform shift assignments, including:

* assignment priority;
* agreed weekly working hours;
* hourly cost;
* skills and proficiency levels;
* recurring availability;
* approved leave requests.

The Employee entity acts as the aggregate root for all scheduling-related information and represents the primary object manipulated 
throughout the application. 
Most business operations ultimately revolve around evaluating employee data in order to determine whether a specific assignment 
can be performed while respecting organizational constraints.

Skills are modeled separately from employees themselves, allowing a single employee to possess multiple competencies 
with different proficiency levels. 
This approach keeps the domain model flexible and extensible while avoiding unnecessary coupling between 
employee information and skill-specific behavior.

# Scheduling Engine

The scheduling engine represents the operational core of the application and contains most of its business logic.
Schedule generation starts from reusable weekly templates that define the staffing requirements for specific days and time ranges. 
Each template describes which skills are required and when they are needed, 
providing the foundation upon which the final schedule is built.

During generation, the engine evaluates available employees and assigns them to schedule slots through a multy phase strategy 
designed to satisfy business requirements while respecting employee constraints.

Among the factors considered during schedule generation are:

* employee skills and proficiency levels;
* assignment priority;
* employee availability;
* approved leave requests;
* agreed weekly working hours.

The scheduling process is intentionally divided into multiple phases. 
Rather than assigning employees through a single pass, the engine progressively improves assignment quality while preserving 
the most critical business requirements. This approach helps ensure that key roles, such as managers or specialized staff members, 
are covered before less critical positions are considered.

Scheduling Phases

1. RESP Assignment:
    Employees possessing the RESP skill are assigned first. 
    Within the restaurant environment, this role represents the most critical position and therefore receives the highest 
    scheduling priority.

2. Critical HIGH Assignment:
    Employees with HIGH assignment priority are processed next. 
    These employees typically represent permanent or long-term contracts that require particular attention 
    to ensure contractual working hours are respected.

3. Protective Rebalance:
    Once critical assignments have been completed, the engine performs an initial optimization phase. 
    The objective is to improve schedule quality while preserving the assignments already made to critical employees.

4. Standard Assignment:
    The remaining uncovered slots are assigned using the pool of available employees that satisfy the required constraints.

5. Residual HIGH Rebalance:
    A final optimization phase attempts to improve the utilization of HIGH-priority employees by balancing remaining working hours 
    and refining previous assignments where possible.

By separating schedule generation into multiple stages, the engine is able to combine operational priorities,
contractual obligations, and employee availability while maintaining a relatively simple and understandable scheduling algorithm.

Schedule Generation Flow Diagram:

`docs/Moka-MokaSE_Schedule_Generator_Flow.drawio.png`

# Persistence Layer

Persistence is implemented through JSON files managed by dedicated repository implementations.
Repositories isolate business logic from storage details, allowing the rest of the application to operate without knowledge 
of how data is physically stored. 
From the perspective of the scheduling engine and the domain model, data access is performed through repository abstractions 
rather than direct interaction with files.

The application currently persists data through three primary files:

* employees.json
* leave-requests.json
* templates.json

Jackson is used to serialize and deserialize domain objects, while repository abstractions provide a consistent access layer 
for the rest of the system.
This design keeps persistence concerns separated from business logic and allows storage implementations to evolve independently 
from the domain model. 
As a result, replacing JSON-based storage with a relational database would require minimal changes to the business layer, 
since most components already depend on repository contracts rather than concrete persistence implementations.

Repository & Persistence Diagram:

`docs/Moka-MokaSE_Repository&JSON_Persistence.drawio.png`

# Console Application

MokaSE was intentionally developed as a console-based application.
User interactions are coordinated through the ManagerConsoleController, which acts as the application's entry point 
and delegates business operations to the service layer. 
This separation allows the controller to focus on managing user input and output while leaving business decisions to 
the underlying application services.
Although simple, the console architecture allowed the project to focus on domain modeling, scheduling logic, 
and software design principles rather than graphical user interface development. 
This choice helped keep the implementation aligned with the educational objectives of the project while maintaining a clear separation
of responsibilities.

Console Runtime Flow Diagram:

`docs/Moka-MokaSE_Console_Runtime_Flow.drawio.png`

# Design Patterns

Several design patterns were adopted throughout the project to improve code organization, maintainability, 
and separation of responsibilities.

* Factory Pattern:
    The EmployeeFactory centralizes employee creation and guarantees that newly created employees are always initialized
    in a valid state.
    By concentrating the creation logic in a dedicated component, 
    the application avoids the risk of creating incomplete or inconsistent employee objects throughout the codebase.

* Composite Pattern: 
    Employee skills are organized through a composite structure in which an employee owns a collection of individual skill 
    objects while exposing them as a unified concept.
    This approach allows the scheduling engine to interact with an employee's competencies as a whole while still 
    preserving the details and behavior associated with each individual skill.

* Repository Pattern:
    Repositories abstract persistence concerns and provide a clear separation between business logic and storage mechanisms.
    The rest of the application interacts with repository contracts rather than directly accessing JSON files, reducing coupling 
    and making future persistence changes easier to implement.

* Iterator Usage:
    Collections are extensively traversed during scheduling operations, persistence activities, reporting, and repository management.
    The project leverages the standard Java Collections Framework and its iteration mechanisms to process employee data, 
    schedule slots, leave requests, and other domain entities in a consistent and maintainable way.

# New Implementations:

https://github.com/ManricoPacenti/MokaWeb
https://github.com/ManricoPacenti/MokaWeb-Frontend
