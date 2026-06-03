Feature: User Registration
  As a visitor to HomeFOOD
  I want to register an account
  So that I can buy or sell homemade food

  Background:
    Given the HomeFOOD registration service is available

  Scenario: Successful buyer registration
    Given I have valid registration details
      | firstName | lastName | email                    | password       | role  |
      | John      | Doe      | john.doe@example.com     | SecurePass123! | BUYER |
    When I submit the registration form
    Then I should receive a 201 status code
    And I should receive an access token
    And the user role should be "BUYER"

  Scenario: Successful seller registration
    Given I have valid seller registration details
      | firstName | lastName | email                      | password       | role   | businessName     |
      | Jane      | Smith    | jane.smith@example.com     | SecurePass456! | SELLER | Jane's Kitchen   |
    When I submit the registration form
    Then I should receive a 201 status code
    And I should receive an access token
    And the user role should be "SELLER"

  Scenario: Registration fails with duplicate email
    Given a user already exists with email "existing@example.com"
    When I try to register with email "existing@example.com"
    Then I should receive a 400 status code
    And the response should contain "Email already registered"

  Scenario: Registration fails with invalid email format
    Given I provide an invalid email "not-valid-email"
    When I submit the registration form
    Then I should receive a 400 status code

  Scenario: Registration fails with short password
    Given I provide a password with less than 8 characters "short"
    When I submit the registration form
    Then I should receive a 400 status code

  Scenario: OTP verification flow
    Given a user is registered with phone "+919876543210"
    When I request an OTP for phone "+919876543210"
    Then an OTP should be sent
    When I verify the OTP correctly
    Then phone should be marked as verified
