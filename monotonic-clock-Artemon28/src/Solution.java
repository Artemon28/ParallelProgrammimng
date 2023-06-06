import org.jetbrains.annotations.NotNull;

/**
 * В теле класса решения разрешено использовать только финальные переменные типа RegularInt.
 * Нельзя volatile, нельзя другие типы, нельзя блокировки, нельзя лазить в глобальные переменные.
 *
 * @author :Чайков Артемий
 */
public class Solution implements MonotonicClock {
    private final RegularInt c1 = new RegularInt(0);
    private final RegularInt c2 = new RegularInt(0);
    private final RegularInt l1 = new RegularInt(0);
    private final RegularInt l2 = new RegularInt(0);
    private final RegularInt c3 = new RegularInt(0);

    @Override
    public void write(@NotNull Time time) {
        // write right-to-left
        l1.setValue(time.getD1());
        l2.setValue(time.getD2());
        c3.setValue(time.getD3());
        c2.setValue(time.getD2());
        c1.setValue(time.getD1());
    }

    @NotNull
    @Override
    public Time read() {
        RegularInt v1 = new RegularInt(0);
        v1.setValue(c1.getValue());
        RegularInt w1 = new RegularInt(0);
        w1.setValue(c2.getValue());
        RegularInt x = new RegularInt(0);
        x.setValue(c3.getValue());
        RegularInt w2 = new RegularInt(0);
        w2.setValue(l2.getValue());
        RegularInt v2 = new RegularInt(0);
        v2.setValue(l1.getValue());
        if (v1.getValue() != v2.getValue()){
            return new Time(v2.getValue(), 0, 0);
        } else if (w1.getValue() != w2.getValue()){
            return new Time(v2.getValue(), w2.getValue(), 0);
        }

        return new Time(v2.getValue(), w2.getValue(), x.getValue());
    }
}
