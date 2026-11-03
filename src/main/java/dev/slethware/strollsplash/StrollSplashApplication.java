package dev.slethware.strollsplash;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
		info = @Info(
				contact = @Contact(
						name = "Slethware The Dev",
						email = "slethware@gmail.com"
				),
				description = "Splash screen rotation service: serves the pool of splash videos the mobile client may play",
				title = "StrollSplash API Documentation",
				version = "1.0"
		)
)
@SpringBootApplication
public class StrollSplashApplication {

	public static void main(String[] args) {
		SpringApplication.run(StrollSplashApplication.class, args);
	}

}
