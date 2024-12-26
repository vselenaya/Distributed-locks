package solution;

import internal.Environment;
import java.util.Queue;  // подулючаем интерфейс Queue
import java.util.LinkedList;  // а тут уже реалзация этого интерфейса


public class ProcessCentralized implements MutexProcess {
    private final Environment env;
    Queue<Integer> queue_for_cs = new LinkedList<>();  // очередь номеров процессов, желающих войти в критическую секцию (cs)
    boolean is_cs_free = true;  // свободна ли cs сейчас? (это и предыдуще нужно только координатору, чей номер = 1)

    public ProcessCentralized(Environment env) {
        this.env = env;
    }


    // Функция разрешает вход в критическую секцию процессу с номером pid (функция только для координатора нужна):
    private void allow_cs(int pid) {
        is_cs_free = false;  // раз разрешаем вход для pid, значит cs занята становится
        if (pid == 1)
            env.lock();  // если координатор разрешает сам себе вход, то он просто входит (делает lock)
        else
            env.send(pid, "ok");  // иначе приходится посылать сообщение ok, чтобы уведомить pid-процесс
    }


    // Функция, которая вызывается у процесса, которому пришлом сообщение message от процесса sourcePid:
    @Override
    public void onMessage(int sourcePid, Object message) {
        if (env.getProcessId() == 1) {  // Если текущий процесс (для которого сообщение) - координатор
            if (message.equals("need_cs")) {  // если сообщение запрашивает вход в cs
                if (is_cs_free)
                    allow_cs(sourcePid);  // коорднатор либо пускает (если она свободна)
                else
                    queue_for_cs.add(sourcePid);  // либо ставит в очередь
            }

            if (message.equals("release_cs")) {  // если сообщение о выходе из cs
                if (!queue_for_cs.isEmpty())
                    allow_cs(queue_for_cs.remove());  // координатор либо пускает первого в очереди
                else
                    is_cs_free = true;  // либо (есл очередь опустела), помечает cs свободной
            }
        } else {  // Если данный процесс - обычный
            if (message.equals("ok"))
                env.lock();  // ему интересно только сообщение ok, по которому он сразу захватывает cs
        }
    }


    // Функция, которая вызывается у процесса, которому захотелось войти в cs:
    @Override
    public void onLockRequest() {
        if (env.getProcessId() == 1) {  // Если текущий процесс - координатор, он сам может решить
            if (is_cs_free)
                allow_cs(1);  // войти в cs, если свободна
            else
                queue_for_cs.add(1);  // или встать в очередь
        } else  // Если обычный процесс, ему нужно попросить коорднатора
            env.send(1, "need_cs");
    }


    // Функция, которая вызывается у процесса, когда он выходит из cs:
    @Override
    public void onUnlockRequest() {
        env.unlock();  // первым делом он отпускает lock на критической секции
        if (env.getProcessId() == 1) {  // Далее как обычно - если координатор, то он сам все решает
            if (!queue_for_cs.isEmpty())
                allow_cs(queue_for_cs.remove());
            else
                is_cs_free = true;
        } else  // Иначе уведомляем
            env.send(1, "release_cs");
    }
}

// !!! Важно в этом задании, чтобы координатор не отправлял сообщения самому себе (а сам принимал решения, если ему
// нужно войти/выйти в критическую секцию) - это логично на практике , так как сообщения по сети - медленно