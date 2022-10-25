package transaction.stage2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 트랜잭션 전파(Transaction Propagation)란? 트랜잭션의 경계에서 이미 진행 중인 트랜잭션이 있을 때 또는 없을 때 어떻게 동작할 것인가를 결정하는 방식을 말한다.
 * <p>
 * FirstUserService 클래스의 메서드를 실행할 때 첫 번째 트랜잭션이 생성된다. SecondUserService 클래스의 메서드를 실행할 때 두 번째 트랜잭션이 어떻게 되는지 관찰해보자.
 * <p>
 * https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#tx-propagation
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Stage2Test {

    private static final Logger log = LoggerFactory.getLogger(Stage2Test.class);

    @Autowired
    private FirstUserService firstUserService;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * service1: REQUIRED
     * service2: REQUIRED
     * <p>
     * 생성된 트랜잭션이 몇 개인가?
     * 👉 1개
     * <p>
     * 왜 그런 결과가 나왔을까?
     * 👉 REQUIRED는 기존 트랜잭션이 있으면 사용하고 없으면 새로 생성한다.
     */
    @Test
    void testRequired() {
        final var actual = firstUserService.saveFirstTransactionWithRequired();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .contains("transaction.stage2.FirstUserService.saveFirstTransactionWithRequired");
    }

    /**
     * service1: REQUIRED
     * service2: REQUIRES_NEW
     * <p>
     * 생성된 트랜잭션이 몇 개인가?
     * 👉 2
     * <p>
     * 왜 그런 결과가 나왔을까?
     * 👉 REQUIRES_NEW는 기존 트랜잭션이 있으면 일시중지 후 새로 만든다.
     */
    @Test
    void testRequiredNew() {
        final var actual = firstUserService.saveFirstTransactionWithRequiredNew();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(2)
                .contains(
                        "transaction.stage2.SecondUserService.saveSecondTransactionWithRequiresNew",
                        "transaction.stage2.FirstUserService.saveFirstTransactionWithRequiredNew"
                );
    }

    /**
     * firstUserService.saveAndExceptionWithRequiredNew()에서 강제로 예외를 발생시킨다.
     * REQUIRES_NEW 일 때 예외로 인한 롤백이 발생하면서 어떤 상황이 발생하는지 확인해보자.
     */
    @Test
    void testRequiredNewWithRollback() {
        assertThat(firstUserService.findAll()).hasSize(0);

        assertThatThrownBy(() -> firstUserService.saveAndExceptionWithRequiredNew())
                .isInstanceOf(RuntimeException.class);

        assertThat(firstUserService.findAll()).hasSize(1);
    }

    /**
     * service1: -
     * service2: SUPPORTS
     * <p>
     * FirstUserService.saveFirstTransactionWithSupports() 메서드를 보면 @Transactional이 주석으로 되어 있다.
     * 주석인 상태에서 테스트를 실행했을 때와 주석을 해제하고 테스트를 실행했을 때 어떤 차이점이 있는지 확인해보자.
     * <p>
     * 👉 SUPPORTS는 기존 트랜잭션이 있으면 사용하고 없으면 트랜잭션 없이 로직 수행
     *  @Transactional을 비활성화 한 경우에도 테스트 결과는 1개라고 뜨지만 로그를 확인하면 active false라고 뜬다.
     */
    @Test
    void testSupports() {
        final var actual = firstUserService.saveFirstTransactionWithSupports();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(1)
                .contains("transaction.stage2.FirstUserService.saveFirstTransactionWithSupports");
    }

    /**
     * FirstUserService.saveFirstTransactionWithMandatory() 메서드를 보면 @Transactional이 주석으로 되어 있다.
     * 주석인 상태에서 테스트를 실행했을 때와 주석을 해제하고 테스트를 실행했을 때 어떤 차이점이 있는지 확인해보자.
     * SUPPORTS와 어떤 점이 다른지도 같이 챙겨보자.
     *
     * 👉 MANDATORY는 기존 트랜잭션 있으면 사용하고 없으면 예외 발생시킨다.
     */
    @Test
    void testMandatory() {
        assertThatThrownBy(() -> firstUserService.saveFirstTransactionWithMandatory());
    }

    /**
     * service1: REQUIRED
     * service2: NOT_SUPPORTED
     *
     * 아래 테스트는 몇 개의 물리적 트랜잭션이 동작할까?
     * 👉 1
     *
     * FirstUserService.saveFirstTransactionWithNotSupported() 메서드의 @Transactional을 주석처리하자.
     * 다시 테스트를 실행하면 몇 개의 물리적 트랜잭션이 동작할까?
     * 👉0
     *
     * 스프링 공식 문서에서 물리적 트랜잭션과 논리적 트랜잭션의 차이점이 무엇인지 찾아보자.
     * 👉 물리적 트랜잭션 : DB에 적용되는 트랜잭션.
     * 👉 논리적 트랜잭션 : 서비스 단에서 적용하는 트랜잭션.
     */
    @Test
    void testNotSupported() {
        final var actual = firstUserService.saveFirstTransactionWithNotSupported();

        log.info("transactions : {}", actual);
        assertThat(actual)
                .hasSize(2);
    }

    /**
     * service1: REQUIRED
     * service2: NESTED
     *
     * 아래 테스트는 왜 실패할까? FirstUserService.saveFirstTransactionWithNested() 메서드의 @Transactional을 주석 처리하면 어떻게 될까?
     *
     * 👉 NESTED는 기존 트랜잭션이 있으면 save point를 표시해 로직에서 예외 발생 시 해당 save point로 롤백한다.
     * 기존 트랜잭션이 없으면 REQUIRED와 동일하게 동작한다.
     * NESTED는 JPA에서 지원하지 않아 현재 오류가 발생한다. (https://en.wikibooks.org/wiki/Java_Persistence/Transactions)
     */
    @Test
    void testNested() {
        assertThatThrownBy(() -> firstUserService.saveFirstTransactionWithNested());
    }

    /**
     * service1: REQUIRED
     * service2: NEVER
     *
     * 마찬가지로 @Transactional을 주석처리하면서 관찰해보자.
     *
     * 👉 NEVER은 기존 트랜잭션이 존재하면 예외가 발생한다.
     */
    @Test
    void testNever() {
        assertThatThrownBy(() ->firstUserService.saveFirstTransactionWithNever());
    }
}
