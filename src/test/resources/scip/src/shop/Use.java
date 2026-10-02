package shop;

import java.util.List;

public class Use {
    public static double total(List<Shape> shapes) {
        double sum = 0;
        for (Shape s : shapes) sum += s.area();
        return sum;
    }

    public static void main(String[] args) {
        System.out.println(total(List.of(new Square(2), new Square(3))));
    }
}
