import org.jetbrains.annotations.NotNull;

/**
 * В теле класса решения разрешено использовать только финальные переменные типа RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author : Темников Алексей
 */
public class Solution implements MonotonicClock {
    private final RegularInt c1 = new RegularInt(0);
    private final RegularInt c2 = new RegularInt(0);
    private final RegularInt c3 = new RegularInt(0);

    private final RegularInt c_1 = new RegularInt(0);
    private final RegularInt c_2 = new RegularInt(0);
    private final RegularInt c_3 = new RegularInt(0);

    @Override
    public void write(@NotNull Time time) {
        // write right-to-left
        c1.setValue(time.getD1());
        c2.setValue(time.getD2());
        c3.setValue(time.getD3());

        c_3.setValue(c3.getValue());
        c_2.setValue(c2.getValue());
        c_1.setValue(c1.getValue());
    }

    @NotNull
    @Override
    public Time read() {
        // read left-to-right
        final int res1 = c_1.getValue();
        final int res2 = c_2.getValue();
        final int res3 = c_3.getValue();

        final int res_3 = c3.getValue();
        final int res_2 = c2.getValue();
        final int res_1 = c1.getValue();

        if (res1 == res_1) {
            if (res2 == res_2) {
                if (res3 == res_3) {
                    return new Time(res1, res2, res3);
                }
                return new Time(res1, res2, res_3);
            }
            return new Time(res1, res2, Integer.MAX_VALUE);
        }
        return new Time(res1, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }
}
