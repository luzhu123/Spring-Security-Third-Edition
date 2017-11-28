package com.packtpub.springsecurity.configuration;

import org.jasig.cas.client.session.SingleSignOutFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetailsByNameServiceWrapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.access.intercept.FilterSecurityInterceptor;
import org.springframework.security.web.authentication.logout.LogoutFilter;

/**
 * Spring Security Config Class
 * @see {@link WebSecurityConfigurerAdapter}
 * @since chapter11.00
 */
@Configuration
@EnableWebSecurity(debug = true)
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Import(CasConfig.class)

// Thymeleaf needs to use the Thymeleaf configured FilterSecurityInterceptor
// and not the default Filter from AutoConfiguration.
@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private static final Logger logger = LoggerFactory
            .getLogger(SecurityConfig.class);

    @Autowired
    private AuthenticationUserDetailsService authenticationUserDetailsService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private CasAuthenticationEntryPoint casAuthenticationEntryPoint;

    @Autowired
    private CasAuthenticationFilter casFilter;

    @Autowired
    private CasAuthenticationProvider casAuthenticationProvider;

    @Value("#{systemProperties['cas.server']}/logout")
    private static String casServerLogout;

    @Autowired
    private SingleSignOutFilter singleSignOutFilter;


    /**
     * Configure AuthenticationManager.
     *
     * NOTE:
     * Due to a known limitation with JavaConfig:
     * <a href="https://jira.spring.io/browse/SPR-13779">
     *     https://jira.spring.io/browse/SPR-13779</a>
     *
     * We cannot use the following to expose a {@link UserDetailsManager}
     * <pre>
     *     http.authorizeRequests()
     * </pre>
     *
     * In order to expose {@link UserDetailsManager} as a bean, we must create  @Bean
     *
     * @see {@link super.userDetailsService()}
     * @see {@link com.packtpub.springsecurity.service.DefaultCalendarService}
     *
     * @param auth       AuthenticationManagerBuilder
     * @throws Exception Authentication exception
     */
    @Override
    public void configure(final AuthenticationManagerBuilder auth) throws Exception {
//        super.configure(auth);
        auth.authenticationProvider(casAuthenticationProvider);
    }


    /**
     * BCryptPasswordEncoder password encoder
     * @return
     */
    @Description("Standard PasswordEncoder")
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder(4);
    }


    /**
     * HTTP Security configuration
     *
     * <pre><http auto-config="true"></pre> is equivalent to:
     * <pre>
     *  <http>
     *      <form-login />
     *      <http-basic />
     *      <logout />
     *  </http>
     * </pre>
     *
     * Which is equivalent to the following JavaConfig:
     *
     * <pre>
     *     http.formLogin()
     *          .and().httpBasic()
     *          .and().logout();
     * </pre>
     *
     * @param http HttpSecurity configuration.
     * @throws Exception Authentication configuration exception
     *
     * @see <a href="http://docs.spring.io/spring-security/site/migrate/current/3-to-4/html5/migrate-3-to-4-jc.html">
     *     Spring Security 3 to 4 migration</a>
     */
    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // Matching
        http.authorizeRequests()
                // FIXME: TODO: Allow anyone to use H2 (NOTE: NOT FOR PRODUCTION USE EVER !!! )
                .antMatchers("/admin/h2/**").permitAll()

                .antMatchers("/").permitAll()
                .antMatchers("/login/*").permitAll()
                .antMatchers("/logout").permitAll()
                .antMatchers("/signup/*").permitAll()
                .antMatchers("/errors/**").permitAll()
                .antMatchers("/admin/*").access("hasRole('ADMIN') and isFullyAuthenticated()")
                .antMatchers("/events/").hasRole("ADMIN")
                .antMatchers("/**").hasRole("USER");

        http.addFilterAt(casFilter, CasAuthenticationFilter.class);

        http.addFilterBefore(singleSignOutFilter, LogoutFilter.class);

        // Logout
        http.logout()
                .logoutUrl("/logout")
                .logoutSuccessUrl(casServerLogout)
                .permitAll();

        // Anonymous
        http.anonymous();

        // CSRF is enabled by default, with Java Config
        http.csrf().disable();


        // Exception Handling
        http.exceptionHandling()
                .authenticationEntryPoint(casAuthenticationEntryPoint)
                .accessDeniedPage("/errors/403")
        ;


        // Enable <frameset> in order to use H2 web console
        http.headers().frameOptions().disable();
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManager()
            throws Exception {
        return super.authenticationManager();
    }

    @Bean
    public UserDetailsByNameServiceWrapper authenticationUserDetailsService(
            final UserDetailsService userDetailsService){
        return new UserDetailsByNameServiceWrapper(){{
            setUserDetailsService(userDetailsService);
        }};
    }




    /**
     * This is the equivalent to:
     * <pre>
     *     <http pattern="/resources/**" security="none"/>
     *     <http pattern="/css/**" security="none"/>
     *     <http pattern="/webjars/**" security="none"/>
     * </pre>
     *
     * @param web
     * @throws Exception
     */
    @Override
    public void configure(final WebSecurity web) throws Exception {

        // Ignore static resources and webjars from Spring Security
        web.ignoring()
                .antMatchers("/resources/**")
                .antMatchers("/css/**")
                .antMatchers("/webjars/**")
        ;

        // Thymeleaf needs to use the Thymeleaf configured FilterSecurityInterceptor
        // and not the default Filter from AutoConfiguration.
        final HttpSecurity http = getHttp();
        web.postBuildAction(() -> {
            web.securityInterceptor(http.getSharedObject(FilterSecurityInterceptor.class));
        });
    }

} // The End...
