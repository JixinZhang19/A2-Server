package apiPresentation.dto.out;

/**
 * @author Rebecca Zhang
 * Created on 2024-06-02
 */
public class SkierOutDto<T> {

    private String message;
    private T data;

    public SkierOutDto(String message, T data) {
        this.message = message;
        this.data = data;
    }

}
