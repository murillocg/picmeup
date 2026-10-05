package com.picmeup.common.config;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;

class SpaForwardControllerTest {

    private final SpaForwardController controller = new SpaForwardController();

    private Object handle(String path) {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.NOT_FOUND.value());
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, path);
        return controller.handleError(request);
    }

    @Test
    void anOrdinaryPathStillReachesTheSinglePageApp() {
        assertThat(handle("/events/butterfly-effect-melbourne"))
                .isInstanceOf(ModelAndView.class)
                .extracting(m -> ((ModelAndView) m).getViewName())
                .isEqualTo("forward:/index.html");
    }

    @Test
    void aDeepRouteWithNoExtensionStillReachesTheApp() {
        assertThat(handle("/my-events"))
                .isInstanceOf(ModelAndView.class);
    }

    /**
     * The scan that exhausted the heap probed 2,700 of these. Each one used to render the
     * whole single-page app to answer a request that could only ever miss.
     */
    @Test
    void archiveProbesGetACheap404() {
        for (String path : new String[]{
                "/secret.zip", "/monthly_backup.zip", "/db.sql", "/.env",
                "/config.ini", "/shell.php", "/backup.tar.gz", "/old.bak"}) {
            assertThat(handle(path))
                    .describedAs(path)
                    .isInstanceOf(ResponseEntity.class);
            assertThat(((ResponseEntity<?>) handle(path)).getStatusCode())
                    .describedAs(path)
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void probesAreMatchedRegardlessOfCaseOrQueryString() {
        assertThat(handle("/Backup.ZIP")).isInstanceOf(ResponseEntity.class);
        assertThat(handle("/dump.sql?x=1")).isInstanceOf(ResponseEntity.class);
    }

    /**
     * A legitimate event slug could plausibly end in something that looks like an
     * extension, so matching must be on the real suffix only.
     */
    @Test
    void aSlugThatMerelyContainsAProbeWordIsNotTreatedAsOne() {
        assertThat(handle("/events/zip-line-championships")).isInstanceOf(ModelAndView.class);
        assertThat(handle("/events/the-env-open")).isInstanceOf(ModelAndView.class);
    }

    @Test
    void apiPathsStillGoToTheErrorPage() {
        assertThat(handle("/api/unknown"))
                .isInstanceOf(ModelAndView.class)
                .extracting(m -> ((ModelAndView) m).getViewName())
                .isEqualTo("forward:/error-page");
    }
}
