package sample;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class Graph {
    double adjMatrix[][];
    int numOfVehicles;
    Depot d;
    ArrayList<Location> c;  //depot at index 0 , customer continue 1-4
    double dist;
    Queue<Integer> q;
    LinkedList<Location> linkedList = new LinkedList<>();
    ArrayList<Vehicle> vehicleList = new ArrayList<>();
    StringBuilder sb;
    double routeCost;
    double tourCost;

    //params for MCTS
    int N;
    int ALPHA;
    double z;
    double[][][] policy;
    double[][] globalPolicy;
    ArrayList<Location> location;
    Tour best_tour = new Tour(Double.POSITIVE_INFINITY);
    int timeLimit = 60;

    public Graph(ArrayList<Location> c){
        this.c = c;
        d=(Depot)c.get(0);
        N = c.size();
        policy = new double[3][N][N];
        globalPolicy = new double[N][N];
        numOfVehicles = 0;
        q = new LinkedList<>();
        adjMatrix = new double[c.size()][c.size()]; //store distance between every 2 node (customer and depot),customer and customer
        //forming the graph
        for (int i = 0; i < c.size(); i++) { //drawing the edge
            for (int j = 0; j < c.size(); j++) {
                if(i==j) continue;
                if(adjMatrix[i][j]!=0)
                    continue; //because save before
                dist = Math.sqrt(Math.pow(c.get(i).xCoordinate-c.get(j).xCoordinate,2) + Math.pow(c.get(i).yCoordinate-c.get(j).yCoordinate,2));

                adjMatrix[i][j] = adjMatrix[j][i] = dist;  //because is undirected edge
            }
        }
        location = (ArrayList<Location>) c.clone();
    }

    public void displayEdges(){
        for (int i = 0; i < adjMatrix.length; i++) {
            for (int j = 0; j < adjMatrix.length; j++) {
                System.out.print(adjMatrix[i][j] + " ");
            }
            System.out.println();
        }
    }

    public int getAdjUnvisitedVertex(int v){
        for (int i = 1; i < c.size(); i++) {
            Customer cus=(Customer)c.get(i);  //Always need to downcast
            if(!cus.wasVisited && adjMatrix[v][i]>0){
                return c.get(i).id;
            }
        }
        return -1;
    }

    public ArrayList<Vehicle> bfs2(){
        List<Integer> adjList[]= new LinkedList[c.size()-1];  //adjacency list as different route combination
        double [] cost=new double[c.size()-1];      //cost of the adjList[i] route
        int [] accumulatedSize=new int[c.size()-1];

        boolean [][] visited = new boolean[c.size()-1][c.size()-1];
        //store visited customer for every customer
        //2D array because for each adjList, every customer must be visited

        for (int i = 0; i < adjList.length; i++) {
            adjList[i]=new LinkedList<>();
            adjList[i].add(0);   //every route will start from depot
        }

        for (int i = 0; i < adjList.length; i++) { //bfs first level
            adjList[i].add(i + 1);  //add every possible customer as first visit
            cost[i] += adjMatrix[0][i + 1];  //depot to first customer
            accumulatedSize[i]+=c.get(i+1).demandSize;
            visited[i][i]=true;  //first node visited
        }
        while(!completeArrayVisited(visited)) {  //everytime start this line means a new level of bfs
            int x;

            for (int i = 0; i < adjList.length; i++) {
                x=adjList[i].get(adjList[i].size()-1);  //get last element of list
                int minNode=0;
                double min=Double.POSITIVE_INFINITY;
                for (int j = 1; j < adjMatrix.length; j++) {  //check distance to adjacent node of customer i
                    if(visited[i][j-1])  //visited node in the list
                        continue;
                    if(adjMatrix[x][j]<min && accumulatedSize[i]+c.get(j).demandSize<= d.maximumCapacity ) {
                        min = adjMatrix[x][j];  //update the route with cheapest cost
                        minNode=j;
                    }

                }
                if(minNode!=0 ) {  //there exists a customer demand size that still can be add to the vehicle
                    cost[i] += min;
                    adjList[i].add(minNode);
                    accumulatedSize[i] += c.get(minNode).demandSize;
                    visited[i][minNode-1]=true;
                }
                else{  //no customer demand size can fit in the vehicle anymore, need new vehicle
                    accumulatedSize[i]=0;
                    cost[i] += adjMatrix[adjList[i].get(adjList[i].size()-1)][0]; //go back to depot
                    adjList[i].add(0);   //act as ending for previous route and also starting of next vehicle

                }
            }
            if(completeArrayVisited(visited)) {  //every customer visited, now return to depot and terminate
                for (int i = 0; i < adjList.length; i++) {
                    if(adjList[i].get(adjList[i].size()-1)!=0) {
                        cost[i] += adjMatrix[adjList[i].get(adjList[i].size() - 1)][0];
                        adjList[i].add(0);
                    }
                }
                break;
            }
        }

        double minCost=Double.POSITIVE_INFINITY;
        int minIndex=0;
        for (int i = 0; i < adjList.length; i++) {
            if(cost[i]<minCost){
                minCost=cost[i];
                minIndex=i;
            }
        }


        int index=1;
        linkedList.clear();
        int  currentLoad=0;
        linkedList.add(c.get(0));
        for (int i = index; i < adjList[minIndex].size(); i++) {  //separate the minCost path into different vehicle
            if(adjList[minIndex].get(i) != 0) {
                linkedList.add(c.get(adjList[minIndex].get(i)));
                currentLoad+=c.get(adjList[minIndex].get(i)).demandSize;
                continue;
            }
            index=i;
            linkedList.add(c.get(0));
            routeCost = computeRouteCost(linkedList);
            vehicleList.add(new Vehicle(linkedList, routeCost,currentLoad));
            linkedList.clear();
            currentLoad=0;
            linkedList.add(c.get(0));
        }
        sb = new StringBuilder();
        sb.append("Basic Simulation Tour\nTour Cost: " + minCost + "\n");
        displayVehicle2(vehicleList);

        resetVisited();
        return vehicleList;


    }

    public boolean completeArrayVisited(boolean [] [] visited){
        for (int i = 0; i < visited.length; i++){
            for(int j = 0; j <visited[0].length; j++) {
                if (!visited[i][j])
                    return false; //still have unvisited node

            }
        }
        return true;
    }

//    public void bfs() {
//        double shortestPath;
//        int currentLoad;
//        tourCost=0;
//
//        q.add(1); //start from 1 , because Customer class start from index 1
//        int v1=q.peek();//take the first customer
//        int v2; //check the nodes adjacent to it, since this is a complete undirected graph, the nodes adjacent to customer 1 is 2,3,4
//        while((v2=getAdjUnvisitedVertex(v1))!=-1){ //Queue = {1,2,3,4}
//            ((Customer)(c.get(v2))).wasVisited=true;
//            q.add(v2);
//        }
//
//        for (int i = 1; i < c.size(); i++) { //resetting all to false to create connections later
//            ((Customer)(c.get(i))).wasVisited=false;
//        }
//
//
//        while(!completeVisited()){
//            linkedList.clear();
//            linkedList.add (c.get(0)); //add depot
//            currentLoad=0;
//            shortestPath=Double.POSITIVE_INFINITY;
//            if( ((Customer)c.get(q.peek())).wasVisited){
//                q.remove();
//            }else{
//                v2 = q.remove();
//                ((Customer)c.get(v2)).wasVisited = true;
//                currentLoad+=c.get(v2).demandSize;
//                linkedList.add (c.get(v2));  //linkedList for the vehicle
//                int temp=0; //customer ID
//                for (int i = 1; i < c.size(); i++) { //create a temporary shortest path here (e.g. 1->2, we know later it will be replaced by 3)
//                    Customer cus=(Customer) c.get(i);
//                    if (!cus.wasVisited && (currentLoad + cus.demandSize) <= d.maximumCapacity) {  //d.demandSize= depot MaximumCapacity
//                        shortestPath = adjMatrix[v2][i];
//                        temp = i;
//                        break;
//                    }
//                }
//                for (int k = 1; k < c.size(); k++) { //find the shortest path
//                    Customer cus=(Customer) c.get(k);
//                    if (!cus.wasVisited && (currentLoad + cus.demandSize) <= d.maximumCapacity) {
//                        if(shortestPath > adjMatrix[v2][k]){
//                            shortestPath = adjMatrix[v2][k];
//                            temp = k;
//                        }
//                    }
//                }
//                if(temp==0){//temp==0 means there is only one node(e.g. 0->4->0)
//                    linkedList.add (c.get(0)); //add depot
//                    routeCost = computeRouteCost(linkedList);
//                    vehicleList.add(new Vehicle(linkedList, routeCost,currentLoad));
//                }else{//(e.g. 0->1->3->0)
//                    Customer cus=(Customer)c.get(temp);
//                    cus.wasVisited=true;
//                    currentLoad+=cus.demandSize;
//                    linkedList.add(cus);
//                    linkedList.add (c.get(0)); //add depot
//                    routeCost = computeRouteCost(linkedList);
//                    vehicleList.add(new Vehicle(linkedList, routeCost,currentLoad));
//                }
//                tourCost+=routeCost;
//
//            }
//        }
//
//        //display output
//        System.out.println("Basic Simulation Tour" + "\nTour Cost: " + tourCost);
//        displayVehicle(vehicleList);
//    }

    public boolean completeVisited(){
        for (int i = 1; i < c.size(); i++) {
            if(!((Customer)c.get(i)).wasVisited) return false;
        }
        return true;
    }

    public double computeRouteCost(LinkedList<Location> linkedList){
        double routeCost=0;

        for (int i = 0; i < linkedList.size()-1; i++) {
            int x= linkedList.get(i).id;
            int y= linkedList.get(i+1).id;
            routeCost+=adjMatrix[x][y];
        }
        return routeCost;
    }

    public void displayVehicle(ArrayList<Vehicle> vehicleList){
        for (int i = 1; i <= vehicleList.size(); i++) {
            System.out.println("Vehicle " + i);
            System.out.println(vehicleList.get(i-1));
        }
    }

    public String displayVehicle2(ArrayList<Vehicle> vehicleList){
        for (int i = 1; i <= vehicleList.size(); i++) {
            sb.append("\nVehicle " + i +"\n" + vehicleList.get(i-1) + "\n");
        }
        return sb.toString();
    }

    public ArrayList<Vehicle> greedySearch(){
        int currentLoad;
        tourCost=0;
        ArrayList<Location> greedyList=new ArrayList<>();
        linkedList.clear();
        vehicleList.clear();

        for (int i = 1; i < c.size(); i++) { //resetting all to false to create connections later
            ((Customer)(c.get(i))).wasVisited=false;
        }

        int i=0;  //start from depot
        while(!completeVisited()) {  //if true , greedyList has been generated, can terminate
            //double [] array=new double[adjMatrix.length];//to compare their distance from i node

            double shortest = Double.POSITIVE_INFINITY;
            Customer tempCustomer = (Customer) c.get(1);
            for (int j = 1; j < adjMatrix.length; j++) { //to get shortest distance
                Customer cus = (Customer) c.get(j);
                if (i == j)
                    continue;
                if(cus.wasVisited)
                    continue;
                if (adjMatrix[i][j] < shortest) { //greedy search only consider shortest distance between two node
                    shortest = adjMatrix[i][j];
                    tempCustomer = cus;
                }

            }
            shortest=Double.POSITIVE_INFINITY;
            greedyList.add(tempCustomer);
            tempCustomer.wasVisited=true;

            i = tempCustomer.id; //got error
        }
        for ( i = 1; i < c.size(); i++) { //resetting all to false to create connections later
            ((Customer)(c.get(i))).wasVisited=false;
        }
        tourCost=vehicleDistribution(greedyList);
        //display output

        sb = new StringBuilder();
        sb.append("Greedy Simulation Tour\nTour Cost: " + tourCost + "\n");
        displayVehicle2(vehicleList);
        resetVisited();
        return vehicleList;
    }

    public ArrayList<Vehicle> AStarSearch(){
        tourCost = 0;
        ArrayList<Location> closed = new ArrayList<>();  //a list storing visited by not yet expand node
        ArrayList<Location> open = new ArrayList<>();   //a list storing visited and expanded node
        List<Double> g= new ArrayList<>();  //a list storing straight line distance, h(n)
        for (int i = 0; i < adjMatrix[0].length; i++) {
            g.add(adjMatrix[0][i]);
        }
        for (int i = 0; i < g.size(); i++) {  //list that store location according to h(n)-> distance to goal node
            closed.add(c.get(i));
        }

        open.add(closed.remove(0)); //start exploring with first node in open
        double startToCurrent=0;
        //f(n)=h(n)+g(n)
        while(!closed.isEmpty()){ //must travel until closed list is all visited
            List<Double> f=new ArrayList<>(); //first copy the distance to goal(depot)
            Location current=open.get(open.size()-1); //expand the node chosen
            int currentID=current.id;
            double min=Double.POSITIVE_INFINITY;
            int minIndex=0;
            for (int i = 1; i <=closed.size(); i++) {
                double h= startToCurrent+ adjMatrix[currentID][closed.get(i-1).id]+ g.get(i);   //distance from current node to next node
                f.add(h); //set the heuristic function
            }
            for (int i = 0; i < f.size(); i++) {
                if(f.get(i)<min){
                    min=f.get(i);
                    minIndex=i;
                }
            }
            startToCurrent+=adjMatrix[currentID][closed.get(minIndex).id];
            open.add(closed.remove(minIndex));//the node is being visited and expanded, remove from closed
            g.remove(minIndex);
        }

        open.remove(0);
        tourCost=vehicleDistribution(open);

        //display output
        sb = new StringBuilder();
        sb.append("A* Search Simulation Tour\nTour Cost: " + tourCost + "\n");
        displayVehicle2(vehicleList);
        return vehicleList;
    }

    public double vehicleDistribution(ArrayList<Location> list){
        tourCost=0;
        linkedList.clear();
        vehicleList.clear();

        for (int i = 1; i < c.size(); i++) { //resetting all to false to create connections later
            ((Customer)(c.get(i))).wasVisited=false;
        }
        int i=0;
        while(!completeVisited()) {  //for loop to form different combination of customer , makesure all customer are visited
            //suppose is while there are no node unvisited
            linkedList.clear();
            int currentLoad = 0;
            double tempShortestNode=adjMatrix[0][0];  //distance depot to depot = zero
            int tempShortestNodeIndex=0;
            while (i < list.size()) {
                //traverse through greedy list to end to check if there if any combination can be formed, which capacity won't be overload
                if (currentLoad + list.get(i).demandSize<= d.maximumCapacity  && !((Customer)c.get(i+1)).wasVisited) {
                    double shortestFirstNode = adjMatrix[0][list.get(i).id];
                    if (shortestFirstNode < tempShortestNode) {
                        linkedList.add(tempShortestNodeIndex, list.get(i));
                    } else if (linkedList.size() >= 2) {
                        linkedList.add(tempShortestNodeIndex + 1, list.get(i));
                    } else
                        linkedList.add(list.get(i)); //add Customer at the end , but we need to formed the route starting from location closest to the depot
                    ((Customer)c.get(i+1)).wasVisited=true;
                    tempShortestNode = shortestFirstNode;
                    tempShortestNodeIndex = linkedList.indexOf(list.get(i));
                    currentLoad += list.get(i).demandSize;
                }
                i++;
            }
            linkedList.addFirst(c.get(0)); //add depot
            linkedList.add(c.get(0));//complete the path with return to depot
            routeCost = computeRouteCost(linkedList);
            vehicleList.add(new Vehicle(linkedList, routeCost,currentLoad));

            tourCost+=routeCost;
            linkedList.clear();
            i=0;
        }
        return tourCost;
    }



    public void resetVisited(){
        for (int i = 1; i < c.size() ; i++) {
            ((Customer)c.get(i)).wasVisited = false;
        }
    }

    public Tour mctsSearch(int level, int iterations){
        Instant startTime = Instant.now();
        if(level==0){
            return rollout();
        }else{
            policy[level-1] = globalPolicy;
            for (int i = 0; i < iterations; i++) {
                Tour new_tour = mctsSearch(level-1,iterations);
                if(new_tour.getTourCost() < best_tour.getTourCost()){
                    best_tour = new_tour;
                    adapt(best_tour,level);
                }
                if(Duration.between(startTime, Instant.now()).getSeconds() > timeLimit);
            }
            globalPolicy = policy[level-1];
        }
        return best_tour;
    }

    public void adapt(Tour a_tour, int level){
        ArrayList<Location> visited = new ArrayList<>();
        //for every route in tour
        for (int i = 0; i < a_tour.getRouteSize(); i++) {
            for (int j = 0; j < a_tour.getRoute().get(i).size()-1; j++) {
                ALPHA=1;
                policy[level-1][a_tour.getRoute().get(i).get(j).id][a_tour.getRoute().get(i).get(j+1).id] += ALPHA;
                z = 0.0;
                //for every possible move that can be made by stop
                for (int k = 0; k < location.size(); k++) {
                    if(location.get(k).id!=a_tour.getRoute().get(i).get(j).id){
                        if(!visited.contains(location.get(k))){
                            z+= Math.exp(globalPolicy[a_tour.getRoute().get(i).get(j).id][location.get(k).id]);
                        }
                    }
                }
                //for every possible move that can be made by stop
                for (int k = 0; k < location.size(); k++) {
                    if(location.get(k).id != a_tour.getRoute().get(i).get(j).id){
                        if(!visited.contains(location.get(k))){
                            policy[level - 1][a_tour.getRoute().get(i).get(j).id][location.get(k).id] -= ALPHA * (Math.exp(globalPolicy[a_tour.getRoute().get(i).get(j).id][location.get(k).id]) / z);
                        }
                    }
                }
                visited.add(a_tour.getRoute().get(i).get(j));
            }
        }
    }

    public Tour rollout(){
        Location currentStop;
        Location nextStop;

        ArrayList<Location> possible_successors = (ArrayList<Location>)location.clone();
        possible_successors.remove(0); //remove the depot

        ArrayList<Location> visited = new ArrayList<>();
        ArrayList<Location> checked = new ArrayList<>();

        Tour new_tour = new Tour(adjMatrix,c);
        new_tour.addNewRoute();

        int currentLoad = 0;

        while(true){
            currentStop = new_tour.getLastStop();
            for (int i = 0; i < possible_successors.size(); i++) {
                if(checked.contains(possible_successors.get(i)) || visited.contains(possible_successors.get(i))){
                    possible_successors.remove(i);
                }
            }
            //if no possible successor is available, return to depot
            if(possible_successors.isEmpty()){
                new_tour.addDepot(); //add depot
                //!!!setRouteCost;
                //if all stops are visited
                if(checked.isEmpty()) {
                    //!!!user for loop to compute Tour cost;
                    break; //rollout process is done
                }
                //add new route into new tour
                new_tour.addNewRoute();
                currentLoad = 0;

                for (int i = 0; i < checked.size(); i++) {
                    possible_successors.add(checked.remove(i));
                }
                continue; // skip to next loop to continue
            }
            nextStop = select_next_move(currentStop,possible_successors);

            if(currentLoad+nextStop.demandSize<=d.maximumCapacity){
                new_tour.addStop(nextStop);
                currentLoad += nextStop.demandSize;
                visited.add(nextStop);
            }else{
                checked.add(nextStop);
            }
        }
        return new_tour;
    }

    public Location select_next_move(Location currentStop, ArrayList<Location> possible_successors){ //possible successors = {0,1,2,3,4}
        double[] probability = new double[possible_successors.size()];
        double sum = 0;
        for (int i = 0; i < possible_successors.size(); i++) {
            probability[i] = Math.exp(globalPolicy[currentStop.id][possible_successors.get(i).id]);
            sum+=probability[i];
        }
        double mRand = new Random().nextDouble() * sum;
        int j = 0;
        sum = probability[0];
        while(sum<mRand){
            sum+=probability[++j];
        }
        return possible_successors.get(j);
    }

    public void displayTour(){
        System.out.println(mctsSearch(3,100));
    }

}