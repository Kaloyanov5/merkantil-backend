package github.kaloyanov5.merkantil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableCaching
// escapeCharacter ensures `Containing` derived queries escape user-supplied % and _ via SQL ESCAPE clause
@EnableJpaRepositories(basePackages = "github.kaloyanov5.merkantil.repository", escapeCharacter = '\\')
public class MerkantilApplication {

	public static void main(String[] args) {
		SpringApplication.run(MerkantilApplication.class, args);
	}

}
