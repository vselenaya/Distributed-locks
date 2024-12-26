package solution;

import internal.Environment;
import java.util.PriorityQueue;

// Pair, PairComparator, MyMessage - те же, что в алгоритме Лампорта


public class ProcessRickartAgrawala implements MutexProcess {
    private final Environment env;
    private int time_lamport = 0;  // Время Лампорта данного процесса
    private int ok_cnt = 0;  // Снова счётчик ok, которыми разрешается нам входть в cs от других
    private final int INF = Integer.MAX_VALUE;  // Бесконечность - очень большое число
    private int self_desire_cs = INF;  // Время, когда сам процесс захотел войти в cs (INF - значит, не хочет)
    private final PriorityQueue<Pair> queue_requests = new PriorityQueue<>(new PairComparator());  // Очередь (pid, t)
    // (процессов, которые хотели в cs, но которым ok ещё не ответили: pid - номер процесса, t - когда он захотел в cs)

    public ProcessRickartAgrawala(Environment env) {
        this.env = env;
    }


    // Функция процесса, которая пытается войти в критическую секцию:
    private void try_to_cs() {
        if (ok_cnt == env.getNumberOfProcesses())  // В данном алгоритме нам достаточно собрать ok от всех процессов
            env.lock();  // и тогда заходим в cs
    }


    // Функция, вызывающаяся у процесса при поступлении ему сообщения:
    @Override
    public void onMessage(int sourcePid, Object message) {
        MyMessage msg = (MyMessage) message;  // тут как и в алгоритме Лампорта - получаем сообщение и обновляем время
        time_lamport = Integer.max(time_lamport, msg.time) + 1;

        if (msg.str.equals("need_cs")) {  // Если процесс sourcePid хочет войти в cs
            var req = new Pair(msg.val, sourcePid);  // считаем пару: время, когда он захотел войти и его номер
            var me = new Pair(self_desire_cs, env.getProcessId());  // и то же для нашего процесса
            var r = (new PairComparator()).compare(req, me);
            if (r < 0)  // если у него время меньше нашего (или если его pid меньше при равенстве времени)
                env.send(sourcePid, new MyMessage(time_lamport, "ok", 0));  // отправляем ему ok
            else                                                             // (= разрешаем ему войти в cs перед нами)
                queue_requests.add(req);  // иначе ставим его в очередь неотвеченных сообщений
            return;
        }
        // (заметим, что случай, когда мы уже находимся в cs отдельно разбирать не нужно - так как раз мы вошли,
        // значит нам все ответили ok, а значит к тому моменту раньше нас никто не хотел в cs -> время, когда они
        // захотели больше нашего self_desire_cs, поэтому мы им ok не отвтим)

        if (msg.str.equals("ok")) {  // Если нам прислали очередной ok (ответ на наше желание войти в cs)
            ok_cnt += 1;
            try_to_cs();  // увеличиваем счётчик и пытаемся войти
        }
    }


    // Функция, вызывающаяся у процесса, когда ему захотелось войти в cs:
    @Override
    public void onLockRequest() {
        ok_cnt = 1;  // пока что у нас есть только один ok от самих себя
        self_desire_cs = time_lamport;  // запоминаем время, когда мы захотели войти в cs
        for (int pid = 1; pid <= env.getNumberOfProcesses(); pid ++)
            if (pid != env.getProcessId())  // всем кроме себя рассылаем сообщение, указывая self_desire_cs обязательно
                env.send(pid, new MyMessage(time_lamport, "need_cs", self_desire_cs));
        try_to_cs();  // пытаемся войти (тут войдём только, если мы едиснвтенный процесс)
    }


    // Функция, вызывающаяся у процесса, если ему нужной выйти из cs:
    @Override
    public void onUnlockRequest() {
        env.unlock();  // Сразу выходим
        self_desire_cs = INF;  // Сбрасываем время (теперь больше не хотим в cs)
        while (!queue_requests.isEmpty()) {
            var x = queue_requests.poll();  // всем, кто был в очереди, то есть хотел войти в cs, досылаем ok
            env.send(x.second, new MyMessage(time_lamport, "ok", 0));
        }
    }
}
