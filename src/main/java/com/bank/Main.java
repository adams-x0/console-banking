package com.bank;

import com.bank.controller.CustomerController;
import com.bank.controller.EmployeeController;
import com.bank.controller.UserController;
import com.bank.repository.AccountDAO;
import com.bank.repository.UserDAO;
import com.bank.service.AccountService;
import com.bank.service.UserService;

import java.sql.SQLException;


public class Main {

    public static void main(String[] args) {
        UserDAO userDAO = new UserDAO();
        AccountDAO accountDAO = new AccountDAO();

        UserService userService = new UserService(userDAO);
        AccountService accountService = new AccountService(accountDAO, userDAO);

        CustomerController customerController =
                new CustomerController(accountService);

        EmployeeController employeeController =
                new EmployeeController(userService, accountService);

        UserController userController = new UserController(
                userService,
                customerController,
                employeeController
        );

        userController.run();
    }
}
