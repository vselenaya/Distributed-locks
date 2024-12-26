package solution;

import internal.Environment;

import java.io.Serializable;
import java.util.PriorityQueue;
import java.util.Comparator;


// Класс для хранения пары int:
class Pair {
    int first;
    int second;

    Pair(int first, int second) {
        this.first = first;
        this.second = second;
    }
}


// Класс с функцией, сравнивающей две пары чисел:
class PairComparator implements Comparator<Pair> {
    @Override
    public int compare(Pair p1, Pair p2) {
        if (p1.first != p2.first)  // как и в C++ с std::pair<int, int>, сравниваем по первому, затем по второму
            return Integer.compare(p1.first, p2.first);
        else
            return Integer.compare(p1.second, p2.second);
    }
}


// Собственный класс для сообщений:
class MyMessage implements Serializable {
    int time, val;
    String str;

    MyMessage(int time, String str, int val) {
        this.time = time;  // Время (Лампорта) отправки сообщения - нужно для корректной работы часов
        this.str = str;  // Часть сообщения в виде строки
        this.val = val;  // Ещё часть в виде числа (тут будет обычно время, когда процесс захотел войти в cs)
    }
}


public class ProcessLamport implements MutexProcess {
    private final Environment env;
    private int time_lamport = 0;  // Текущее время Лампорта у процесса
    private int ok_cnt = 0;  // Счётчик сообщений ok, которые процесс получает на запрос входа в cs
    private final PriorityQueue<Pair> queue_for_cs = new PriorityQueue<>(new PairComparator());  // Очередь пар (t, pid)
    // (желающих войти в cs: pid - номер процесса, который захотел в cs в момент своего Лампортовского времени t)
    private String log;  // На всякий случай (для отладки) добавляем log

    public ProcessLamport(Environment env) {
        this.env = env;
        this.log = "=== LOG: Наш pid = " + env.getProcessId() + ", всего процессов: " + env.getNumberOfProcesses() + " ===\n";
    }


    // Функция, которая обрабатывает желание процесса с номером who войти в cs, которое у него появилось во время when:
    private void request_for_cs(int who, int when) {
        Pair record = new Pair(when, who);  // создаёем пару
        queue_for_cs.add(record);  // добавляем её в очередь: сравнение сначала будет по времени, затем по номеру
    }


    // Функция, обрабатывающая освобождение cs (то есть когда процесс с номером who освободил cs, прислав сообщение
    // release, к примеру) - для этого нужно удалить из очереди все записи (а точнее она должна быть только одна)
    // с желанием процесса who войти в cs:
    private void release_cs(int who) {
        PriorityQueue<Pair> temp = new PriorityQueue<>(new PairComparator());
        while (!queue_for_cs.isEmpty())  // перекалдываем все элементы во временную очередь temp
            temp.add(queue_for_cs.poll());

        int cnt = 0;
        while(!temp.isEmpty()) {  // теперь наоборот - из temp перекладываем обратно, но фильтруем записи с who
            var x = temp.poll();
            if (x.second != who)
                queue_for_cs.add(x);
            else  // подсчитываем количество записей, где был процесс who (номер процесса - второй элемент пары!)
                cnt += 1;
        }

        if (cnt != 1)  // тут проверяем корректность - важно, что нельзя писать assert, так как тестирующая система
                       // спецально даёт некорректные тесты, ожидая падение программы - но это падение должно
                       // быть через throw IllegalStateException
            throw new IllegalStateException("В очереди каждый pid может быть только один раз\n!" + log);

        /*
        Тут важно сделать замечание относительно всего алгоритма Лампорта:
        1) Во-первых, в условии задания нам гарантируется, что onLockRequest не будет вызыван повторно, пока
        процесс не выйдет из критической секции -> так как выход собровождается сообщением всем release (что приводит
        к удаленю вышедшего из cs процесса из очередей), то два раза в одной очереди один и тот же процесс всетриться
        не может (так как новый запрос на вход в cs придёт от него точно позже release из-за FIFO-порядка). Это мы
        и проверяем через (cnt != 1).
        2) Казалось бы, так как вход в критическую секцию происходит первым в очереи процессом только после получения от
        всех процессов сообщения "ok" и так как очереди упорядочены по логическому (Лампортовскому) времени, то вошедший
        в cs процесс будет самым первым в очередях на всех процессах системы. Казалось бы, что таким образом,
        получаем мы release тоже от процесса самого первого в очерееди - и нам достаточно вызвать queue_for_cs.poll()
        без перекладываний в доп очередь...
        Но на самом деле это не совсем правда, так как может быть ситуация, что один процесс (пусть A) вышел из cs
        и отправил release остальным процессам (B и C). Пусть процесс B, получив release, тоже входит в cs (так как
        он был следующим за A в очереди и успел собрать "ok" от остальных заранее) и тут же выходит, тоже отправляя
        release остальным процессам. Но из-за задержек в сети может быть так, что процесс C сначала получит release
        от процесса B, а только потом от процесса A (заметим, что это никак не нарушает FIFO и вполне может происходить
        в алгоритме Лампорта) - в этом случае (так как A первее был в очереди, чем B) процессу C придётся сначала
        удалить запись о процессе B из середины очереди и только по приходу сообщения от A удалить его... поэтому
        всё же необходимо делать так, как у нас написано - искать элемент who и удалять именно его, а не просто первый
        элемент в очереди... правда, можно было как-то поэффективнее сделать, чем просто гонять очередь туда-сюда, за
        линию выискивая элемент who. Но так точно корректно.
        Вообщем вывод такой: иногда release может прийти от процесса в середине очереди (но только в случае, если
        более ранние процессы в очереди уже вышли из cs, а release от них просто не успел прийти).
         */
    }


    // Функця проверяет, может ли текущий процесс войти в cs - и если да, то заходит:
    private void try_to_cs() {
        if (!queue_for_cs.isEmpty() &&  // если вообще есть желающие на cs
            queue_for_cs.peek().second == env.getProcessId() &&  // если текущий процесс - первый в очереди
            ok_cnt == env.getNumberOfProcesses())  // и при этом все ok (от всех процессов) собраны
            env.lock();  // то заходим в cs
    }


    // Функция, которая вызывается у процесса, когда к нему приходит сообщение:
    @Override
    public void onMessage(int sourcePid, Object message) {
        MyMessage msg = (MyMessage) message;  // превращаем объект сообщения в наш тип
        time_lamport = Integer.max(time_lamport, msg.time) + 1;  // обновляем свои (те процесса, которому пришёл msg)
        // часы Лампорта по правилам: берем максимум и +1 (заметим, что часы Лампорта нам нужны только для сравнения
        // времени между процессами, поэтому данное место - единственное, где нам обязательно обновлять часы...
        // в остальных местах (при отправке сообщений или других событиях) можем не обновлять, так как время внутри
        // процесса (между его событиями) нас не интересует)
        log += "Msg от pid = " + sourcePid + ", его время: " + msg.time + ", str: " + msg.str + ", val: " + msg.val + "\n";

        if (msg.str.equals("need_cs")) {  // Если пришёл запрос на вход cs
            request_for_cs(sourcePid, msg.val);  // Обрабатываем это и тут же отвечаем ok
            env.send(sourcePid, new MyMessage(time_lamport, "ok", 0));  // (значение val не важно - пусть 0)
            return;
        }

        if (msg.str.equals("release")) {  // Если сообщили об освобождении cs
            release_cs(sourcePid);  // обрабатываем это
            try_to_cs();  //  и пробуем сами войти
            return;
        }

        if (msg.str.equals("ok")) {  // Если нам прислали ok
            ok_cnt += 1;  // наращиваем счётчик и пробуем войти
            try_to_cs();
        }
    }


    // Функция вызывается, когда процесс захотел в cs:
    @Override
    public void onLockRequest() {
        log += "---> мы захотели в cs во время: " + time_lamport + "\n";
        int time = time_lamport;  // запомнили время, когда захотели в cs
        request_for_cs(env.getProcessId(), time);  // сами себя не забыли обработать
        ok_cnt = 1;  // захотели в cs -> начинаем считать ok (один, от самих себя, уже есть)
        for (int pid = 1; pid <= env.getNumberOfProcesses(); pid ++)
            if (pid != env.getProcessId())  // каждому процессу (кроме себя) отправляем запрос
                env.send(pid, new MyMessage(time_lamport, "need_cs", time));
        try_to_cs();  // сразу попытаемся войти - вдруг уже можно (тут войдем в cs только, если 1 процесс в системе)
    }


    // Функция, вызываемая, когда хотим выйти из cs:
    @Override
    public void onUnlockRequest() {
        log += "<--- выходим из cs, время: " + time_lamport + "\n";
        env.unlock();  // тут же отпускаем захват, выходя из cs
        release_cs(env.getProcessId());  // обрабатываем сами себя
        for (int pid = 1; pid <= env.getNumberOfProcesses(); pid ++)
            if (pid != env.getProcessId())  // всех оповещаем
                env.send(pid, new MyMessage(time_lamport, "release", 0));
    }
}


