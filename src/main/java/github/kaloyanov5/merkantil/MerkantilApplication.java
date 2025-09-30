package github.kaloyanov5.merkantil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
public class MerkantilApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerkantilApplication.class, args);
	}

}
