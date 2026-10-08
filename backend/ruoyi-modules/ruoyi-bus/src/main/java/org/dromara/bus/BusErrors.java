package org.dromara.bus;

import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RestControllerAdvice(basePackages = "org.dromara.bus")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BusErrors {
    @ExceptionHandler(BusError.class)
    public R<Void> handle(BusError error) { return R.fail(error.code, error.getMessage()); }
}
