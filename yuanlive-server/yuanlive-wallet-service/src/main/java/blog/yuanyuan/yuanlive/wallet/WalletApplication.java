package blog.yuanyuan.yuanlive.wallet;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("blog.yuanyuan.yuanlive.wallet.mapper")
@EnableFeignClients(basePackages = "blog.yuanyuan.yuanlive.feign")
@ComponentScan(basePackages = {"blog.yuanyuan.yuanlive"})
public class WalletApplication {
    public static void main(String[] args) {
        SpringApplication.run(WalletApplication.class, args);
    }
}
