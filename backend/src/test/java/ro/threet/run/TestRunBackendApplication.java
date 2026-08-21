package ro.threet.run;

import org.springframework.boot.SpringApplication;

public class TestRunBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(RunBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
