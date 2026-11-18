package com.junzhou.membership;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class MembershipMoneyTest {
 @Test void rejectsFractionalFenAndNegativeMoney(){assertEquals(123,MembershipService.fen(new BigDecimal("1.23")));assertThrows(IllegalArgumentException.class,()->MembershipService.fen(new BigDecimal("1.234")));}
 @Test void missingScopeFailsClosed(){assertThrows(SecurityException.class,()->new MembershipService(null,null,null,null).profile());}
}
