/***
File Name : facebookServer.Scala
Project Name : Secure FacebookAPI
Authors : Prerna Mandal ( UFID : 29533906) - prernamandal@ufl.edu
          Jyotsana Walia ( UFID : 83860191) - jwalia@ufl.edu
Course : Disributed Operating Systems(COP5605)
Professor : Alin Dobra
Description : It provides APIs which uses AES, RSA-256 and SHA-256 
to support secure data exchange between client and servers.
School : University of Florida
***/

package FacebookAPI

//spray stuff
import akka.routing.ConsistentHashingRouter
import akka.routing.ConsistentHashingRouter.ConsistentHashMapping
import scala.concurrent._
import akka.pattern.ask
import akka.util.Timeout
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.ArrayList
import scala.collection.mutable.MutableList
import java.security.MessageDigest
import scala.collection.mutable.HashMap
import scala.collection.JavaConversions._

import scala.util.{Success, Failure}
import akka.actor.{ Actor, ActorRef, Props, ActorSystem }
import akka.routing.RoundRobinRouter
import scala.collection.mutable._
import spray.routing.SimpleRoutingApp
import scala.util.Random
import java.io._
import java.util.Date
import java.util.{Date, Locale}


//spray client stuff
import akka.actor._
import spray.routing.SimpleRoutingApp

import org.json4s._
import org.json4s.native.Serialization.{read, write,writePretty}
import spray.http._
import spray.routing.{Route, RequestContext}
import spray.routing.directives._

//for image compression
import java.io.FileInputStream
import javax.imageio.ImageIO
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageWriter
import java.awt.image.BufferedImage
import javax.imageio.ImageWriteParam
import javax.imageio.stream.ImageOutputStream
import java.io.OutputStream
import java.io.InputStream
import javax.imageio.IIOImage
import java.util.Iterator
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream

//for simple copy not limited to images
import java.io.{File,FileInputStream,FileOutputStream}

//Security imports
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import scala.collection.mutable.ArrayBuffer
import akka.actor._
import java.util.Calendar
import java.security.SecureRandom
import javax.crypto.Cipher
import java.security.spec._
import sun.misc.BASE64Decoder
import sun.misc.BASE64Encoder
import java.security.KeyFactory
import java.security.Signature

import java.util.regex.Pattern;
import java.util.regex.Matcher;




case class Profile(userName: String,dob: String, gender:String, phoneNumber:String, emailId:String, image : String, isPage : Int) extends Serializable
case class ProfileList(profileList : ArrayBuffer[Profile])
case class ProfileInfo(userName:String,profileObject:Profile) extends Serializable
case class SetProfileInfoOfUser(userCount: Int,dob:String,gender:String, phoneNumber:String)
case class SetProfileInfoOfPage(userCount: Int,dob:String,gender:String, phoneNumber:String)
case class GetProfileInfoOfUser(userName:String)

case class ProfileMap(userName: String, profileObject : Profile)
case object GetProfileMap

//added now
case class ProfileMapForAll(profileMap:HashMap[String,Profile])
case class GetProfileMapOfAllUsers(start:Int,limit:Int)

//friendlist
case class UpdateFriendListOfUser(friendList : List[String],action:String)
case class AddToFriendListMapOfCache(userName:String, friendList:List[String])
case class FriendListMap(friendlist:HashMap[String,List[String]])
case class GetFriendListOfUser(userName:String)

//page owner list
case class AddToPageOwnerListMapOfCache(userName:String, pageOwnerList:List[String])

//posts
case class Post(author: String,content: String,likeCount: Int,shareCount: Int)
case class PostMapForTheUser(postMap:HashMap[String,Post])
case class CreatePost(content:String,postId:String)
case class PostMapOfAll(postMapOfAll:HashMap[String,HashMap[String,Post]])
case class GetPostMapOfAllUsers(start:Int,limit:Int)
case class PostMapForAll(userName:String, postMapForTheUser:HashMap[String,Post])
case class LikePost(postId:String, actionUserId:Int)
case class UserPostMap(postMap:HashMap[String,Post])
case class GetPostOfUser(username:String,actionUserName:String)
case class GetPostOfUserByPostId(username:String,actionUserName:String,postId:String)
case class SharePost(postId:String, actionUserId:Int)


//albums
case class CreateAlbum(imageContent:String,imageId:String,albumId:String)
case class ImagePost(author: String,imageContent: String) extends Serializable
case class ImageMapAsAlbumForTheUser(albumName:String, imageMapForTheUser:HashMap[String,ImagePost])
case class GetAlbumOfUser(userName:String)
case class AlbumMap(albumMap:HashMap[String,HashMap[String,ImagePost]])
case class GetPicOfUserByImageId(username : String ,actionUserName:String ,picId : String ,albumId : String)

//security map
case class Secure_RegisterProfileInfoOfUser(userCount: Int,dob:String,gender:String, phoneNumber:String, publicKey:String)
case class Secure_Profile(userName: String, publicKey:PublicKey) extends Serializable
case class SecurePublicKeyMap(username:String, publicKey:PublicKey) extends Serializable
case class RequestRandomNumber(action : String)
case class Random(aesKey : Array[Byte])
case class GetPublicKeyOfUser(userName:String)

//To keep a track of the session
case class SessionMapOfUser(username:String, session:String) extends Serializable

object FacebookServer extends App with SimpleRoutingApp
{ 

  override def main(args: Array[String]) 
  {
    //import system.dispatcher
	  implicit val system = ActorSystem("facebookAPI")
    var pw = new FileWriter("server_log.txt",true)
    pw.write("Hello, Welcome to Server!")
    pw.close()
	  val actorCount: Int = Runtime.getRuntime().availableProcessors()*100
    implicit val timeout =  akka.util.Timeout(50000)
    var userRandomNumberMap = new scala.collection.mutable.HashMap[String,String]()

    var sessionMapOfUser = new scala.collection.mutable.HashMap[String,String]()

	  println("Facebook Server Started....")
    val cache_actor = system.actorOf(Props[CacheMaster], name="cache_actor")
    //println("here")

        lazy val createUserForFb = post {
          path("facebook" / "createUser") {
            //println("createUser....")
                entity(as[FormData]) { fields =>
                    //println("Fields = " + fields)
                     var pw1 = new FileWriter("server_log.txt",true)
                     pw1.write("Hello, createUserForFb \n")
                     pw1.close()
                    val userId = fields.fields(0)._2
                    val dob= fields.fields(1)._2
                    val gender = fields.fields(2)._2
                    val phoneNumber = fields.fields(3)._2
                    val facebookUser_actor = system.actorOf(Props(new FacebookUser(system,cache_actor)),name="facebookUser"+userId) 
                    facebookUser_actor!SetProfileInfoOfUser(userId.toInt,dob,gender,phoneNumber)
                    complete("Done")
            }
          }
        }

        //register users
        lazy val registerUserForFb = post {
          path("facebook" / "secure_registerUser") {
            //println("secure_register....")
                entity(as[FormData]) { fields =>
                    //println("Fields = " + fields)
                     var pw1 = new FileWriter("server_log.txt",true)
                     pw1.write("Hello, createUserForFb \n")
                     pw1.close()
                    val userId = fields.fields(0)._2
                    val dob= fields.fields(1)._2
                    val gender = fields.fields(2)._2
                    val phoneNumber = fields.fields(3)._2
                    val publicKey = fields.fields(4)._2
                    //println("publicKeyString in server : "+publicKey)
                    val facebookUser_actor = system.actorOf(Props(new FacebookUser(system,cache_actor)),name="facebookUser"+userId) 
                    //facebookUser_actor!SetProfileInfoOfUser(userId.toInt,dob,gender,phoneNumber)
                    facebookUser_actor ! Secure_RegisterProfileInfoOfUser(userId.toInt,dob,gender,phoneNumber,publicKey)
                    complete("Done")
            }
          }
        }

        //secure user login
        lazy val secure_login = post {
          path("facebook"/"secure_login"){
            println("secure_login....")
              entity(as[FormData]){ fields =>
                 var pw1 = new FileWriter("server_log.txt",true)
                 pw1.write("Hello, createUserForFb \n")
                 pw1.close()
                val userId = fields.fields(0)._2
                val action = fields.fields(1)._2
                var userName : String = "facebookUser"+userId
                var randomNumberString : String =  createRandomNumber(userName) 

                complete(randomNumberString)
              }
          }
        }

        //secure user logout
        lazy val secure_logout = post {
          path("facebook"/"secure_logout"){
            println("secure_logout....")
              entity(as[FormData]){ fields =>
                 var pw1 = new FileWriter("server_log.txt",true)
                 pw1.write("Hello, secure_logout \n")
                 pw1.close()
                val userId = fields.fields(0)._2
                var userName : String = "facebookUser"+userId
                println("stopSession for " + userName)
                stopSession(userName,"Stopped")
                complete("You are logged out now!")
              }
          }
        }


        lazy val secure_connect = post {
          path("facebook"/"secure_connect"){
            println("secure_connect....")
              entity(as[FormData]){ fields =>
                 var pw1 = new FileWriter("server_log.txt",true)
                 pw1.write("Hello, createUserForFb \n")
                 pw1.close()
                val userId = fields.fields(0)._2
                val randomNumberFromClient : String = fields.fields(1)._2
                val signatureString = fields.fields(2)._2
                val userName = "facebookUser"+userId
                val future = cache_actor ? GetPublicKeyOfUser(userName)
                val publicKey = Await.result(future, timeout.duration).asInstanceOf[PublicKey]
                var randomNumberString = getRandomString(userName)
                
                val result : Boolean = verifySignature(randomNumberString,signatureString,publicKey)
                if(result){
                  println("startSession for " + userName)
                  startSession(userName,"Started")
                }else{
                  println("stopSession for " + userName)
                  stopSession(userName,"Stopped")
                }
                complete{
                  if(result)
                  JsonUtil.toJson("Welcome To Facebook")
                  else
                  JsonUtil.toJson("You are not logged in! Try again")
                }
              }
          }
        }


        lazy val createPageForFb = post {
          path("facebook" / "createPage") {
                entity(as[FormData]) { fields =>
                    var pw2 = new FileWriter("server_log.txt",true)
                    pw2.write("Hello, createPageForFb \n")
                    pw2.close()
                    val userId = fields.fields(0)._2
                    val dob= fields.fields(1)._2
                    val gender = fields.fields(2)._2
                    val phoneNumber = fields.fields(3)._2
                    val facebookUser_actor = system.actorOf(Props(new FacebookUser(system,cache_actor)),name="facebookUser"+userId) 
                    facebookUser_actor!SetProfileInfoOfPage(userId.toInt,dob,gender,phoneNumber)
                    complete("Done")
            }
          }
        }

        lazy val updateFriendListOfTheUser = post {
          path("facebook" / "updateFriendListOfFbUser") {
            //println("bp1....updateFriendListOfTheUser")
            entity(as[FormData]) { fields =>
              val userName = "facebookUser"+fields.fields(0)._2
              val friendUserName = "facebookUser"+fields.fields(1)._2
              val action = fields.fields(2)._2
              var pw3 = new FileWriter("server_log.txt",true)
              pw3.write("Hello, updateFriendListOfTheUser \n")
              pw3.close()
              val facebookUser_actor = system.actorSelection("akka://facebookAPI/user/"+userName)
              val facebookFriend_actor = system.actorSelection("akka://facebookAPI/user/"+friendUserName)
              var friendList = List("facebookUser1", "facebookUser2", "facebookUser3", "facebookUser4", "facebookUser5")
              var friendListOfUser : List[String] = List[String]()
              var friendListOfFriend : List[String] = List[String]()
             
              if(action=="delete"){
                friendListOfUser = List(friendUserName)
                friendListOfFriend = List(userName)// subtract here
              }else if(action == "connect"){
                friendListOfUser = List(friendUserName)
                friendListOfFriend = List(userName)
              }
            
              facebookUser_actor ! UpdateFriendListOfUser(friendListOfUser,action)
              facebookFriend_actor ! UpdateFriendListOfUser(friendListOfFriend,action)
              complete {
                "updated for user="+userName
              }
            }
          }
        }

        lazy val profileInfoOfUserOnFb = get {
        respondWithMediaType(MediaTypes.`application/json`)
              path("facebook" / "getProfileInfoOfUser"/Segment){ userCount =>
                var pw4 = new FileWriter("server_log.txt",true)
                pw4.write("Hello, profileInfoOfUserOnFb \n")
                pw4.close()
                val userName = "facebookUser"+userCount
                //parameters("userName".as[String]) { (userName) =>
                    val actor = system.actorSelection("akka://facebookAPI/user/"+userName)
                   // implicit val timeout =  Timeout(2 seconds)
                   val future = actor ? GetProfileInfoOfUser(userName)
                   val userProfile = Await.result(future, timeout.duration).asInstanceOf[Profile]
                   complete{ 
                   JsonUtil.toJson(userProfile)//change it
                   }
                }
              }

              lazy val getFriendListOfUser = get {
                respondWithMediaType(MediaTypes.`application/json`)
                path("facebook"/"getAllFriendsOfUser"/Segment){  userCount =>
                  var pw5 = new FileWriter("server_log.txt",true)
                  pw5.write("Hello, getFriendListOfUser \n")
                  pw5.close()
                  val userName = "facebookUser"+userCount
                  //val actor = system.actorSelection("akka://facebookAPI/user/"+userCount)
                  val future = cache_actor ? GetFriendListOfUser(userName)
                  val friendList = Await.result(future, timeout.duration).asInstanceOf[FriendListMap]
                  complete{
                    JsonUtil.toJson(friendList)
                  }
                }
              }

        lazy val getAllProfileInfoOfUserOnFb = get {
        respondWithMediaType(MediaTypes.`application/json`)
              path("facebook" / "getProfileOfAllFacebookUsers"/Segment){ start =>
                  //println("here1") 
                   var pw6 = new FileWriter("server_log.txt",true)
                   pw6.write("Hello, getAllProfileInfoOfUserOnFb \n")    
                   pw6.close()             
                   val future = cache_actor ? GetProfileMapOfAllUsers(start.toInt,10)
                   val userProfileHashMap = Await.result(future, timeout.duration).asInstanceOf[ProfileMapForAll]
                   complete{ 
                    //userProfileHashMap
                   JsonUtil.toJson(userProfileHashMap)

                   }
                }
                
              }
            

          lazy val createPost = post {
          path("facebook" / "createPost") {
            //println("bp6....")
                entity(as[FormData]) { fields =>
                    //println("In the post Creation spray server")
                      var pw7 = new FileWriter("server_log.txt",true)
                      pw7.write("Hello, createPost \n")
                      pw7.close()
                      val authorUserId = fields.fields(0)._2
                      val postContent = fields.fields(1)._2
                      val postId = fields.fields(2)._2
                      val signedMessage = fields.fields(3)._2
                      val authorUserName = "facebookUser"+authorUserId

                      val future = cache_actor ? GetPublicKeyOfUser(authorUserName)
                      val publicKey = Await.result(future,timeout.duration).asInstanceOf[PublicKey]
                      val result = verifySignature(postContent,signedMessage,publicKey)
                      //println("result is : "+result)
                      if(result){
                        //println("result again : "+result)
                        //println("authorUserName : "+authorUserName)
                        //val facebookUser_actor = system.actorOf(Props(new FacebookUser(cache_actor)),name="facebookUser"+authorUserName) 
                        val facebookUser_actor = system.actorSelection("akka://facebookAPI/user/"+authorUserName)
                        facebookUser_actor ! CreatePost(postContent,postId)
                        complete("Done")
                      }
                      else{
                        complete("Signature is incorrect")
                      }
            }
          }
        }


          lazy val getAllPostsOfUserOnFb = get {
              respondWithMediaType(MediaTypes.`application/json`)
              path("facebook" / "getPostsOfAllFacebookUsers"/Segment){ start =>
                  //println("in the spray server of get all posts") 
                   var pw8 = new FileWriter("server_log.txt",true)
                   pw8.write("Hello, getAllPostsOfUserOnFb \n")
                   pw8.close()                 
                   val future = cache_actor ? GetPostMapOfAllUsers(start.toInt,10)
                   val userPostsHashMap = Await.result(future, timeout.duration).asInstanceOf[PostMapOfAll]
                   complete{ 
                    //userProfileHashMap
                   JsonUtil.toJson(userPostsHashMap)

                   }
                }
                
              }
            

            lazy val likePostOfUser = post {
              path("facebook"/"likePost"){
                entity(as[FormData]) { fields =>
                  //println("inside likePostOfUser")
                  var pw9 = new FileWriter("server_log.txt",true)
                  pw9.write("Hello, likePostOfUser \n")   
                  pw9.close()  
                  val authorId = fields.fields(0)._2
                  val postId = fields.fields(1)._2
                  val actionUserId = fields.fields(2)._2
                  //val facebookUser_actor = system.actorOf(Props(new FacebookUser(cache_actor)),name="facebookUser"+authorUserName) 
                  val facebookUser_actor = system.actorSelection("akka://facebookAPI/user/"+"facebookUser"+authorId)
                  facebookUser_actor ! LikePost(postId,actionUserId.toInt)
                  complete("Done")
                }
              }
            }
            
            lazy val sharePostOfUser = post {
              path("facebook"/"sharePost"){
              entity(as[FormData]) { fields =>
                  println("inside sharePostOfUser")
                  var pw10 = new FileWriter("server_log.txt",true)
                  pw10.write("Hello, sharePostOfUser \n") 
                  pw10.close()
                  val authorId = fields.fields(0)._2
                  val postId = fields.fields(1)._2
                  val actionUserId = fields.fields(2)._2
                  //val facebookUser_actor = system.actorOf(Props(new FacebookUser(cache_actor)),name="facebookUser"+authorUserName) 
                  val facebookUser_actor = system.actorSelection("akka://facebookAPI/user/"+"facebookUser"+authorId)
                  facebookUser_actor ! SharePost(postId,actionUserId.toInt)
                  complete("Done")
                }
              }
            }

         lazy val addImageToAnAlbum = post {
          path("facebook" / "createAlbum") {
            //println("bp6....")
                entity(as[FormData]) { fields =>
                    //println("In the post Creation spray server")
                      var pw11 = new FileWriter("server_log.txt",true)
                      pw11.write("Hello, addImageToAnAlbum \n") 
                      pw11.close()
                      val authorUserId = fields.fields(0)._2
                      val imageContent = fields.fields(1)._2
                      val imageId = fields.fields(2)._2
                      var albumId = fields.fields(3)._2
                      var signedImage = fields.fields(4)._2
                      var authorUserName = "facebookUser"+authorUserId

                      val future = cache_actor ? GetPublicKeyOfUser(authorUserName)
                      val publicKey = Await.result(future,timeout.duration).asInstanceOf[PublicKey]
                      val result = verifySignature(imageContent,signedImage,publicKey)
                      if(result){
                        //val facebookUser_actor = system.actorOf(Props(new FacebookUser(cache_actor)),name="facebookUser"+authorUserName) 
                        val facebookUser_actor = system.actorSelection("akka://facebookAPI/user/"+authorUserName)
                        facebookUser_actor ! CreateAlbum(imageContent,imageId,albumId)
                        complete("Done")
                      }
                      else{
                        complete("Signature is incorrect")
                      }
                    }
                  }
                }

            lazy val getPostOfUser = get{
            respondWithMediaType(MediaTypes.`application/json`)
                path("facebook"/"getPostOfUser"){
                  entity(as[FormData]) { fields =>
                    //println("inside getPostOfUser")
                    val authorId = fields.fields(0)._2
                    val actionUserId = fields.fields(1)._2
                  //println("getPostOfUser")

                  val userName = "facebookUser"+authorId
                  val actionUserName = "facebookUser"+actionUserId

                  var pw12 = new FileWriter("server_log.txt",true)

                  pw12.write("Hello, getPostOfUser \n") 
                  pw12.close()
                  
                  //val actor = system.actorSelection("akka://facebookAPI/user/"+userCount)
                  val future = cache_actor ? GetPostOfUser(userName, actionUserName)
                  val postMapOfUser = Await.result(future, timeout.duration).asInstanceOf[UserPostMap]
                  
                  complete{
                    JsonUtil.toJson(postMapOfUser)
                  }
                }
              }
            }

            lazy val getPostOfUserByPostId = get{
            respondWithMediaType(MediaTypes.`application/json`)
                path("facebook"/"getPostOfUserByPostId"){
                  entity(as[FormData]) { fields =>
                    //println("inside getPostOfUserByPostId")
                    val authorId = fields.fields(0)._2
                    val actionUserId = fields.fields(1)._2
                    val postId = fields.fields(2)._2
                  //println("getPostOfUser")

                  val userName = "facebookUser"+authorId
                  val actionUserName = "facebookUser"+actionUserId


                  var pw14 = new FileWriter("server_log.txt",true)
                  pw14.write("Hello, getPostOfUserByPostId \n") 
                  pw14.close()
                  var exists = checkIfSessionExists(actionUserName)
                  println("session exists" + exists)
                  if(exists){
                    //val actor = system.actorSelection("akka://facebookAPI/user/"+userCount)
                    val future = cache_actor ? GetPostOfUserByPostId(userName, actionUserName,postId)
                    //val postMapOfUser = Await.result(future, timeout.duration).asInstanceOf[UserPostMap]
                    println("yes!! session - ")
                    val postMapOfUser = Await.result(future, timeout.duration).asInstanceOf[Post]
                    println(postMapOfUser)
                    complete{
                      JsonUtil.toJson(postMapOfUser)
                    }
                  }else{
                    complete{
                    JsonUtil.toJson(Post("ErrorNL","ErrorNL",1,1))
                    }
                    //complete("Bad request! You are not logged in dude!")
                  }

                }
              }
            }

            lazy val getPicOfUserByImageId = get{
              respondWithMediaType(MediaTypes.`application/json`)
                path("facebook"/"getPicOfUserByImageId"){
                  entity(as[FormData]) { fields =>
                    println("inside getPicOfUserByImageId")
                    val authorId = fields.fields(0)._2
                    val actionUserId = fields.fields(1)._2
                    val picId = fields.fields(2)._2
                    val albumId = fields.fields(3)._2

                    val userName = "facebookUser"+authorId
                    val actionUserName = "facebookUser"+actionUserId

                    val actor = system.actorSelection("akka://facebookAPI/user/"+userName)
                    val future = actor ? GetPicOfUserByImageId(userName,actionUserName,picId,albumId)
                    val image = Await.result(future,timeout.duration).asInstanceOf[ImagePost]
                    complete{
                      JsonUtil.toJson(image)
                    }
                }
              }
            }

            lazy val getAllAlbumsOfUser = get{
            respondWithMediaType(MediaTypes.`application/json`)
                path("facebook"/"getAllAlbumsOfUser"/Segment){  userCount =>
                  println("getAllAlbumsOfUser")
                  var pw13 = new FileWriter("server_log.txt",true)
                  pw13.write("Hello, getAllAlbumsOfUser \n") 
                  pw13.close()
                  val userName = "facebookUser"+userCount
                  val actor = system.actorSelection("akka://facebookAPI/user/"+userName)
                  val future = actor ? GetAlbumOfUser(userName)
                  val albumMapOfUser = Await.result(future, timeout.duration).asInstanceOf[AlbumMap]
                  complete{
                    JsonUtil.toJson(albumMapOfUser)
                  }
                }
              }

              lazy val verifyPublicKeyOfUser = get{
                respondWithMediaType(MediaTypes.`application/json`)
                    path("facebook"/"verifyPublicKeyOfUser"/Segment){  userCount =>
                      println("verifyPublicKeyOfUser")
                      var pw14 = new FileWriter("server_log.txt",true)
                      pw14.write("Hello, verifyPublicKeyOfUser \n") 
                      pw14.close()
                      val username = "facebookUser"+userCount
                      val future = cache_actor ? GetPublicKeyOfUser(username)
                      val publicKey = Await.result(future,timeout.duration).asInstanceOf[PublicKey]
                      var publicKeyBytes : Array[Byte] = publicKey.getEncoded()
                      val encoder : BASE64Encoder  = new BASE64Encoder()
                      val publicKeyString : String = encoder.encode(publicKeyBytes)
                      //println("publicKey of userCount = "+ userCount + " : : "+publicKey)
                      complete(publicKeyString) 
                    }
              }

          def verifySignature(randomNumberString:String,signatureString:String,publicKey:PublicKey) : Boolean =
          {
            var decoder : BASE64Decoder = new BASE64Decoder()
            var randomNumber  : Array[Byte] = decoder.decodeBuffer(randomNumberString)
            var signature : Array[Byte] = decoder.decodeBuffer(signatureString)
            val signer : Signature = Signature.getInstance("SHA256withRSA")
            signer.initVerify(publicKey)
            signer.update(randomNumber)
            val bool : Boolean = signer.verify(signature)
            return bool
          }   

          def getRandomString(userName : String) : String =
          {

            val randomNumberStringTimestamp = userRandomNumberMap.get(userName) match{
              case Some(randomNumberStringTimestamp) => randomNumberStringTimestamp
              case None => "Error"
              }  
              var parts = randomNumberStringTimestamp.split("timestamp")
              var randomNumberString : String = parts(0)
              var timestamp : String = parts(1)
              randomNumberString
          } 

          def startSession(userName :String,session:String){
            sessionMapOfUser += (userName -> session)
            //println(sessionMapOfUser)
          }

          def stopSession(userName :String,session:String){
            sessionMapOfUser += (userName -> session)
            //println(sessionMapOfUser)
          }

          def checkIfSessionExists(userName:String) : Boolean = {
           //println("sessionMapOfUser" + sessionMapOfUser + "userName" + userName) 
           var sessionExist : Boolean = sessionMapOfUser.exists(_ == (userName,"Started"))
           //println("sessionExist - " + sessionExist)
             if(sessionExist){
              return true
             }else{
              return false
             }
          }

          def createRandomNumber(userName : String) : String =
          {
            val ranGen = new SecureRandom()
            val aesKey = new Array[Byte](32)
            ranGen.nextBytes(aesKey)

            val encoder : BASE64Encoder  = new BASE64Encoder()
            var randomNumberString : String  = encoder.encode(aesKey)

            val timeStamp = Calendar.getInstance().getTime()
            var randomNumberStringTimestamp : String = randomNumberString + "timestamp" + timeStamp
            userRandomNumberMap += (userName -> randomNumberStringTimestamp)
      
            return randomNumberString
          }
                

  	     startServer(interface = "localhost", port = 8080) {
          createUserForFb ~
          registerUserForFb ~
          secure_login ~
          secure_logout ~
          secure_connect ~
          updateFriendListOfTheUser ~
          profileInfoOfUserOnFb ~
          createPageForFb ~
          getAllProfileInfoOfUserOnFb ~
          getFriendListOfUser ~
          createPost ~
          getAllPostsOfUserOnFb ~
          getPostOfUser ~
          getPostOfUserByPostId ~
          getPicOfUserByImageId ~ //new
          likePostOfUser ~
          addImageToAnAlbum ~
          getAllAlbumsOfUser ~
          sharePostOfUser ~
          verifyPublicKeyOfUser
         }
       }
  }       
	  
  class CacheMaster extends Actor
  {
    val profileMapForAllUsers = new scala.collection.mutable.HashMap[String,Profile]()
    val profileList = new ArrayBuffer[Profile]()
    var userFriendMap = new scala.collection.mutable.HashMap[String,List[String]]()
    var pageOwnerMap = new scala.collection.mutable.HashMap[String,List[String]]()
    var postMapForAllUsers = new scala.collection.mutable.HashMap[String,HashMap[String, Post]]()
    //public keys map
    var publicKeyMap = new scala.collection.mutable.HashMap[String,PublicKey]()
    
    var emptyList : List[String] = List("","","")
    var emptyPostMap = new scala.collection.mutable.HashMap[String,Post]()


    def receive =
    {
      case ProfileMap(userName, profileObject)=>
      {
        profileMapForAllUsers += (userName -> profileObject)
      }

      //public keys for all users
      case SecurePublicKeyMap(userName, publicKey)=>
      {
        publicKeyMap += (userName -> publicKey)
      }

      case GetPublicKeyOfUser(userName) =>
      {
          val publicKey = publicKeyMap.get(userName) match{
            case Some(publicKey) => publicKey 
          } 
            sender ! publicKey
      }

      case PostMapForAll(userName, postMapForTheUser)=>
      {
        println("\n PostMapForAll")
        postMapForAllUsers += (userName -> postMapForTheUser)
        //println("\npostMapForAllUsers : "+postMapForAllUsers)
      }

      case GetProfileMap=>
      {

         for(i<-0 until profileMapForAllUsers.size){
          var userName : String = "facebookUser"+i
           var profileObject = profileMapForAllUsers.get(userName) match{
             case Some(profileObject) =>
             {
               profileList += profileObject
             }
             case None => Profile("Error","Error","Error","Error","Error","Error",0)
             }
      }
      val profileListObject = ProfileList(profileList)
      sender ! profileListObject
      }  

      case GetProfileMapOfAllUsers(start,limit)=>
      {  
      sender ! ProfileMapForAll(profileMapForAllUsers)
      }  

      case GetPostMapOfAllUsers(start,limit)=>
      {  
      sender ! PostMapOfAll(postMapForAllUsers)
      }  

      case AddToFriendListMapOfCache(userName, friendList) => {
          userFriendMap += (userName -> friendList)
      }

      case AddToPageOwnerListMapOfCache(userName, pageOwnerList) => {
        pageOwnerMap += (userName -> pageOwnerList)
        //println("page owner list for page : "+userName)
        //for((k,v) <- pageOwnerMap){
          //println("key:"+k+"\tvalue:"+v)
        //}
      }


      case GetFriendListOfUser(userName) => {
        val friendList : List[String]= userFriendMap.get(userName) match{
          case Some(friendList) => friendList
          case None => emptyList
          }

          var friendListMapOfUser = new scala.collection.mutable.HashMap[String,List[String]]()

          friendListMapOfUser += (userName -> friendList)
        sender ! FriendListMap(friendListMapOfUser)
      }

      case GetPostOfUser(userName,actionUserName) => {
         val friendList : List[String]= userFriendMap.get(userName) match{
          case Some(friendList) => friendList
          case None => emptyList
          }
          if (friendList.contains(actionUserName)){
            val postMap : HashMap[String,Post] = postMapForAllUsers.get(userName) match{
            case Some(postMap) => postMap
            case None => emptyPostMap
            }
            if(postMap.isEmpty){
              //println("hashmap is empty")
              sender ! UserPostMap(emptyPostMap)
            }
            else{
              sender ! UserPostMap(postMap)
            }
          }
          else{
            println("Sorry you are not in the friendlist of this user !")
            sender ! UserPostMap(emptyPostMap)
          }
        
        }

        case GetPostOfUserByPostId(userName,actionUserName,postId) => {
          println("\ninside GetPostOfUserByPostId")
          println("\nactionUserName : "+actionUserName)
          println("\nuserFriendMap inside getPostOfUserByPostId : "+userFriendMap)
          val friendList : List[String]= userFriendMap.get(userName) match{
          case Some(friendList) => friendList
          case None => emptyList
          }
          if (friendList.contains(actionUserName)){
            println("friend is present")
            //println("postMapForAllUsers : "+postMapForAllUsers)
            val postMap : HashMap[String,Post] = postMapForAllUsers.get(userName) match{
            case Some(postMap) => postMap
            case None => emptyPostMap
            }
            val post : Post = postMap.get(postId) match {
              case Some(post) => post
              case None => Post("Error","Error",1,1) 
            }
            println("post being sent : "+post)    
            sender ! post
          }
          else{
            println("Sorry you are not in the friendlist of this user !")
            sender ! Post("Error","Error",1,1)
          }
        }
    }
  }


  //this class actually denotes the user actor of facebook
  class FacebookUser(system:ActorSystem,cache_actor:ActorRef) extends Actor 
  {
    var profileMap = new scala.collection.mutable.HashMap[String, Profile]()
    var secureProfileMap = new scala.collection.mutable.HashMap[String, PublicKey]()
    var userName:String = ""
    var emailId : String = ""
    var isPage : Int = 0
    var image : String = "C:-Users-jyotsana-Desktop-FacebookAPI-facebookHelper-photo.jpg"
    //http://pushstar.com/wp-content/uploads/2014/10/facebook-anonymous-app-300x300.jpg 
    var friendList : List[String] = List[String]()
    var friendCount : Int = 0

    var listOfPosts = List[Post]()
    var postMapForTheUser = new scala.collection.mutable.HashMap[String, Post]()

    var imageMapAsAlbumForTheUser = new scala.collection.mutable.HashMap[String,HashMap[String, ImagePost]]()

    def receive = 
      {    
        case SetProfileInfoOfUser(userCount,dob,gender,phoneNumber)=>
          {    
            userName = "facebookUser"+userCount;
            emailId = userName+"@gmail.com"
            val profileObj = Profile(userName,dob,gender,phoneNumber,emailId,image,isPage)
            putProfile(userName,profileObj)       
          }

          case Secure_RegisterProfileInfoOfUser(userCount,dob,gender,phoneNumber,publicKey)=>
          {
            userName = "facebookUser"+userCount;
            emailId = userName+"@gmail.com"
            val profileObj = Profile(userName,dob,gender,phoneNumber,emailId,image,isPage)
            //val secureProfileObj = Secure_Profile(userName,publicKey)
            putProfile(userName,profileObj)  
            putSecureProfile(userName,publicKey) 
          }

          case SetProfileInfoOfPage(userCount,dob,gender,phoneNumber)=>
          {
            isPage = 1
            userName = "facebookPage"+userCount;
            emailId = userName+"@gmail.com"
            var pageOwnerList = List("facebookUser1", "facebookUser2", "facebookUser3", "facebookUser4", "facebookUser5")
            val profileObj = Profile(userName,dob,gender,phoneNumber,emailId,image,isPage)
            putProfile(userName,profileObj)
            putPageOwnerList(userName,pageOwnerList)     
          }

      case GetProfileInfoOfUser(userName)=>
        { 
          val profileObject = profileMap.get(userName) match{
          case Some(profileObject) => profileObject
          case None => Profile("Error","Error","Error","Error","Error","Error",0)
          }
          sender ! profileObject
        } 

        case UpdateFriendListOfUser(friendList1,action)=>
          {    
                 if(action=="connect"){ 
                 friendList = friendList ::: friendList1
                 }else if(action == "delete"){
                 friendList = friendList diff friendList1 
                 }
                 friendCount = friendList.length
                 putFriendList(userName,friendList)
          }

        case CreatePost(content,postId)=> ////here
          {   
          println("CreatePost.............") 
          val postObj = Post(userName,content,0,0)
          putPostToMapAndCache(userName,postObj,postId)       
          }


          case LikePost(postId , actionUserId) =>
          {
            //println("In case LikePost")
            val actionUserName : String = "facebookUser"+actionUserId
            if (friendList.contains(actionUserName)){
              if(postMapForTheUser.contains(postId)){
                var postObject = postMapForTheUser.get(postId) match{
                  case Some(postObject) => postObject
                  case None => Post("Error","Error",0,0)
                }     
                var newPostObj = Post(postObject.author,postObject.content,postObject.likeCount+1,postObject.shareCount)
                putPostToMapAndCache(userName,newPostObj,postId)
              }
              else{
                println("This post does not belong to author")
              }
            }
            else{
              println("The user is not present in the friend list of the author. Sorry !!, the user cant like the post of the author.")
            }
          }

          case SharePost(postId , actionUserId) =>
          {
            println("In case SharePost")
            val actionUserName : String = "facebookUser"+actionUserId
            if (friendList.contains(actionUserName)){
              if(postMapForTheUser.contains(postId)){
                var postObject = postMapForTheUser.get(postId) match{
                  case Some(postObject) => postObject
                  case None => Post("Error","Error",0,0)
                }  
                var newPostObj = Post(postObject.author,postObject.content,postObject.likeCount,postObject.shareCount+1)   
                putPostToMapAndCache(userName,newPostObj,postId)
                println("Post of "+userName+"shared with facebookUser"+actionUserId)
                var newPostId : String = "username"+postId
                val actionUser_actor = system.actorSelection("akka://facebookAPI/user/"+"facebookUser"+actionUserId)
                actionUser_actor ! CreatePost(postObject.content,newPostId)  
              }
              else{
                println("This post does not belong to author.Sorry !!, the user cant share this post.")
              }
            }
            else{
              println("The user is not present in the friend list of the author. Sorry !!, the user cant share the post of the author.")
            }
          }

        case CreateAlbum(imageContent,imageId,albumId)=>
          {    
          val imageObj = ImagePost(userName,imageContent)
          putImageToMapAndCache(userName,imageObj,imageId,albumId)       
          }

        case GetAlbumOfUser(userName) => {
        println("inside cache - GetAlbumOfUser")
        sender ! AlbumMap(imageMapAsAlbumForTheUser)
        }

        case GetPicOfUserByImageId(userName, actionUserName, picId, albumId) => {
          if (friendList.contains(actionUserName)){
            var imageMap = imageMapAsAlbumForTheUser.get(albumId) match{
              case Some(imageMap) => imageMap
              case None => HashMap("0" -> ("0","0"))
            }
            var image = imageMap.get(picId) match{
              case Some(image) => image
              case None => ImagePost("Error","Error")
            }

            sender ! image
          }
        }

      }
     
      def putProfile(userName :String,profileObj:Profile){
        profileMap += (userName -> profileObj)
        cache_actor ! ProfileMap(userName, profileObj)
      }

      def putSecureProfile(userName : String, publicKeyString:String){
        //Convert PublicKeyString to Byte Stream
        var decoder : BASE64Decoder = new BASE64Decoder()
        var sigBytes2  : Array[Byte] = decoder.decodeBuffer(publicKeyString)

        // Convert the public key bytes into a PublicKey object
        var x509KeySpec : X509EncodedKeySpec = new X509EncodedKeySpec(sigBytes2)
        var keyFact : KeyFactory = KeyFactory.getInstance("RSA")
        var publicKeyUser : PublicKey = keyFact.generatePublic(x509KeySpec)

        //println("publicKey server side : "+publicKeyUser)
        secureProfileMap += (userName -> publicKeyUser)
        cache_actor ! SecurePublicKeyMap(userName,publicKeyUser)
      }

      def putPostToMapAndCache(userName :String,postObj:Post,postId:String){
        println("putting post to map and cache")
        postMapForTheUser += (postId -> postObj)
        cache_actor ! PostMapForAll(userName, postMapForTheUser)
      }

      def putImageToMapAndCache(userName:String,imageObj:ImagePost,imageId:String,albumId:String){
        var imageMap = imageMapAsAlbumForTheUser.get(albumId) match{
                  case Some(imageMap) => imageMap
                  case None => HashMap(imageId -> imageObj)
                }
        imageMap += (imageId -> imageObj)
        imageMapAsAlbumForTheUser += (albumId -> imageMap)
      }


      def transferImagesBetweenClientAndServer(userName:String,destName:String,albumId:String,imageId:String){
            var dir = new File("images/"+userName+"-"+albumId);
            var successful:Boolean  = dir.mkdir();
            val src = new File("common/" + destName + ".jpg")
            val dest = new File("images/"+userName+"-"+albumId+"/"+ destName + "-" +imageId +".jpg")
            if(successful){
            new FileOutputStream(dest) getChannel() transferFrom(
            new FileInputStream(src) getChannel, 0, Long.MaxValue )
            }


      }
      

      def putFriendList(userName :String,friendList:List[String]){ 
        cache_actor ! AddToFriendListMapOfCache(userName, friendList)
      }

      def putPageOwnerList(userName:String,pageOwnerList:List[String]){
        cache_actor ! AddToPageOwnerListMapOfCache(userName, pageOwnerList)
      }


        def imageTransferByBytes(img: BufferedImage): BufferedImage = {
        // obtain width and height of image
        val w = img.getWidth
        val h = img.getHeight
        // create new image of the same size
        val out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        // copy pixels (mirror horizontally)
        for (x <- 0 until w)
          for (y <- 0 until h)
            out.setRGB(x, y, img.getRGB(w - x - 1, y) & 0xffffff)   
        // draw red diagonal line
        for (x <- 0 until (h min w))
          out.setRGB(x, x, 0xff0000)

        out
        }

      def compressImageToTransfer(file: File, filename: String, qualityOfOutPutImage: Float): InputStream = {
        val inputStream = new FileInputStream(file)       
        // Creating An In Memory Output Stream 
        val outPutStream = new ByteArrayOutputStream         
        val image = ImageIO.read(inputStream)   // BufferedImage         
        val writers = ImageIO.getImageWritersByFormatName("jpg")
        val writer = writers.next
        val imageOutputStream = ImageIO.createImageOutputStream(outPutStream) // Image Output Stream
        writer.setOutput(imageOutputStream)         
        val param = writer.getDefaultWriteParam
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT) // Setting Compression Mode         
        // Specifying The Image Quality , We Can Choose The Quality Required
        param.setCompressionQuality(qualityOfOutPutImage)          
        writer.write(null, new IIOImage(image, null, null), param)         
        // Closing The Input and Output Streams
        inputStream.close
        outPutStream.close
        imageOutputStream.close
        writer.dispose                       // Disposing writer
         
        // Creating The InputStream From ByteArrayInputStream
        val fileInputStream: InputStream = new ByteArrayInputStream(outPutStream.toByteArray)
        fileInputStream    // Returned The Compressed Image Input Stream 
      }
    
  }

object JsonUtil{
  
  implicit val formats = native.Serialization.formats(ShortTypeHints(List(classOf[ProfileMap],classOf[UserPostMap],classOf[Post])))
  def toJson(profile:Profile) : String = writePretty(profile)
  def toJson(profileList:ProfileList) : String = writePretty(profileList)
  def toJson(profileMap:ProfileMapForAll) : String = writePretty(profileMap)
  //def toJson(post:Post) : String = writePretty(post)
  def toJson(post:Post) : String = write(post)
  def toJson(postMapOfAll:PostMapOfAll) : String = writePretty(postMapOfAll)
  def toJson(friendlist:FriendListMap) : String = writePretty(friendlist)
  def toJson(userPostsHashMap:UserPostMap) : String = write(userPostsHashMap)
  def toJson(albumMap:AlbumMap) : String = writePretty(albumMap)
  //def toJson(aesKey : Random) : String = writePretty(aesKey)
  def toJson(aesKey : Array[Byte]) : String = writePretty(aesKey)
  def toJson(string : String) : String = write(string)
  def toJson(image : ImagePost) : String = write(image)
  def toJson(publicKey : PublicKey) : String = writePretty(publicKey)
}