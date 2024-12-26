package solution;

import internal.Environment;


public class ProcessToken implements MutexProcess {
    private final Environment env;
    private boolean desire_cs = false;  // Хотим ли мы в cs? изначально нет

    public ProcessToken(Environment env) {
        this.env = env;
        if (env.getProcessId() == 1)  // Изначально токен у первого -> сразу запускам его по кругу, ко второму:
            env.send(get_next(), "");  // (передача токена = посылка пустого сообщения)
    }

    private int get_next() {  // Функция, которая получает следующи (за текущим) по кругу процесс (после 1 идёт 2 и тд)
        int pid = env.getProcessId();
        return pid % env.getNumberOfProcesses() + 1;
    }

    @Override
    public void onMessage(int sourcePid, Object message) {
        if (message.equals("")) {  // если получили токен
            if (desire_cs)
                env.lock();  // то можем войти в cs, если хочется
            else
                env.send(get_next(), "");  // или переслать токен далее
        }
    }

    @Override
    public void onLockRequest() {
        desire_cs = true;  // если захотели в cs - запомнили и ждём токена
    }

    @Override
    public void onUnlockRequest() {
        env.unlock();  // при выходе из cs меняем флаги и посылаем токен дальше (токен у нас пока мы в cs)
        desire_cs = false;
        env.send(get_next(), "");
    }
}
