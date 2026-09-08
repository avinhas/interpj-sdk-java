# inter-sdk-java

# 🛠️ Technical Interview Demo Environment

> **Note for Reviewers & Interviewers:** 
> This repository is a public fork of https://github.com/inter-co/pj-sdk-java. It is being utilized strictly as a non-commercial, sandbox environment for a technical mock demonstration. 

## 📌 Context & Compliance
* **Purpose:** This fork serves as a practical testing ground to demonstrate unit test coverage improvement during the interview process. 
* **License & Copyright:** The upstream repository does not currently feature an explicit open-source license. As such, all original code remains the exclusive copyright of the original author(s). This fork operates strictly within the public viewing and forking permissions granted by GitHub’s Terms of Service. No commercial usage or redistribution is intended.


## Execution Mode

- To execute methods from the InterSdk, an external application that includes it as a project dependency is required.

- To verify that a method is functioning correctly, you can run the functional tests included in the project. These tests can be found in the directory `src/main/java/functests/FunctionalTestRunner.java`.

Running Functional Tests

1. Ensure that you have the necessary dependencies set up in your project.
2. Navigate to the `functests` directory.
3. Execute the `FunctionalTestRunner.java` file to run the tests and check for correct method execution.
4. Review the output to confirm that all tests pass successfully.

This approach will help you ensure that the integration with the InterSdk is working as intended.
