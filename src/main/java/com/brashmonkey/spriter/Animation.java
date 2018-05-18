package com.brashmonkey.spriter;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.utils.Array;
import com.brashmonkey.spriter.math.Curve;

/**
 * Represents an animation of a Spriter SCML file. An animation holds {@link Timeline}s and a {@link Mainline} to
 * animate objects. Furthermore it holds a {@link #length}, a {@link #name} and whether it is {@link
 * #looping} or not.
 *
 * @author Trixt0r
 */
public class Animation
{
	private String name;
	private int length;
	private boolean looping;

	private Mainline mainline;
	private final Array<Timeline> timelines;

	private Array<SpriterObject> tweenedObjects; //sprites made on runtime by tweening original sprites from animation
	private Array<SpriterDrawable> sprites;

	private MainlineKey currentKey;

	/**
	 * Milliseconds
	 */
	private float time = 0;
	private float speed = 1f, alpha = 1f;

	private SpriterObject root = new SpriterObject();

	public Animation(String name, int length, boolean looping, Mainline mainline, Array<Timeline> timelines)
	{
		this.name = name;

		this.length = length;
		this.looping = looping;

		this.mainline = mainline;
		this.timelines = timelines;

		prepare();
	}

	public Animation(Animation animation)
	{
		this.name = animation.getName();

		this.length = animation.length;
		this.looping = animation.looping;

		this.mainline = new Mainline(animation.mainline);
		this.timelines = new Array<>(animation.getTimelines().size);

		for(Timeline timeline : animation.getTimelines())
			timelines.add(timeline.clone());

		prepare();
	}

	public void prepare()
	{
		tweenedObjects = new Array<>(new SpriterObject[timelines.size]);
		sprites = new Array<>(timelines.size);

		for(Timeline timeline : timelines)
		{
			if(timeline instanceof SpriteTimeline)
			{
				SpriterSprite sprite = new SpriterSprite();
				tweenedObjects.set(timeline.getId(), sprite);
				sprites.add(new SpriterDrawable(sprite, ((SpriteTimeline)timeline).getZIndex()));
			}
			else
				tweenedObjects.set(timeline.getId(), new SpriterObject());
		}

		sprites.sort();

		currentKey = mainline.getKeyBeforeTime(0, looping);
	}

	public void draw(Batch batch)
	{
		float prevColor = batch.getPackedColor();
		Color tmp = batch.getColor();
		tmp.a *= alpha;
		batch.setColor(tmp);

		for(SpriterDrawable sprite : sprites)
			sprite.draw(batch);

		batch.setColor(prevColor);
	}

	/**
	 * Updates this player. This means the current time gets increased by {@link #speed} and is applied to the current
	 * animation.
	 */
	public void update()
	{
		update(1000f / 60f); //assume 60 fps by default
	}

	/**
	 * Updates this player. This means the current time gets increased by {@link #speed} and is applied to the current
	 * animation.
	 *
	 * @param delta time in milliseconds
	 */
	public void update(float delta)
	{
		if(tweenedObjects == null)
			throw new IllegalStateException("Animation not prepared");

		//if(time + speed * delta > currentKey.time)

		time += speed * delta;

		if(time >= length)
		{
			if(!looping)
				time = length;
			else
				time -= length;
		}

		int intTime = (int)time;

		MainlineKey currentKey = mainline.getKeyBeforeTime(intTime, looping);

		for(ObjectRef ref : currentKey.objectRefs)
			update(currentKey, ref, intTime);
	}

	protected void update(MainlineKey currentKey, ObjectRef ref, int time)
	{
		//Get the timelines, the refs pointing to
		Timeline timeline = timelines.get(ref.timeline);

		TimelineKey key = timeline.getKeys().get(ref.key); //get the last previous key
		TimelineKey nextKey;

		int timeOfNext;

		if(ref.key + 1 == timeline.getKeys().size)
		{
			if(!looping)
			{
				//no need to tween, stay freezed at first sprite
				SpriterObject tweenTarget = tweenedObjects.get(ref.timeline);

				tweenTarget.set(key.getObject());

				SpriterObject parent = ref.parent != null ? tweenedObjects.get(ref.parent.timeline) : root;
				tweenTarget.unmap(parent);
				return;
			}

			nextKey = timeline.getKeys().get(0);
			timeOfNext = nextKey.getTime() + length; //wrap around
		}
		else
		{
			nextKey = timeline.getKeys().get(ref.key + 1);
			timeOfNext = nextKey.getTime();
		}

		float timeDiff = timeOfNext - key.getTime();
		float timeRatio = currentKey.curve.interpolate(0f, 1f, (time - key.getTime()) / timeDiff);

		//Tween object
		SpriterObject obj1 = key.getObject();
		SpriterObject obj2 = nextKey.getObject();
		SpriterObject tweened = tweenedObjects.get(ref.timeline);

		Curve curve = key.getCurve();

		tweened.setAngle(curve.interpolateAngle(obj1.getAngle(), obj2.getAngle(), timeRatio, key.getSpin()));

		curve.interpolateVector(obj1.getPosition(), obj2.getPosition(), timeRatio, tweened.getPosition());
		curve.interpolateVector(obj1.getScale(), obj2.getScale(), timeRatio, tweened.getScale());
		curve.interpolateVector(obj1.getPivot(), obj2.getPivot(), timeRatio, tweened.getPivot());

		if(timeline instanceof SpriteTimeline)
		{
			((SpriterSprite)tweened).setAlpha(curve.interpolate(((SpriterSprite)obj1).getAlpha(), ((SpriterSprite)obj2).getAlpha(), timeRatio));
			((SpriterSprite)tweened).setAsset(((SpriterSprite)obj1).getAsset());
		}

		tweened.unmap(ref.parent != null ? tweenedObjects.get(ref.parent.timeline) : root);
	}

	public void reset()
	{
		time = 0;
		update(0);
	}

	public SpriterObject getRoot()
	{
		return root;
	}

	public Mainline getMainline()
	{
		return mainline;
	}

	public Array<Timeline> getTimelines()
	{
		return timelines;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	/**
	 * Time is in milliseconds
	 *
	 * @return current time of this animation
	 */
	public float getTime()
	{
		return time;
	}

	public void setTime(float time)
	{
		while(time < 0)
			time += length;

		if(looping)
		{
			while(time >= length)
				time -= length;
		}
		else if(time > length)
			time = length;

		this.time = time;
	}

	public float getSpeed()
	{
		return speed;
	}

	public void setSpeed(float speed)
	{
		this.speed = speed;
	}

	public float getAlpha()
	{
		return alpha;
	}

	public void setAlpha(float alpha)
	{
		this.alpha = alpha;
	}

	public int getLength()
	{
		return length;
	}

	public void setLength(int length)
	{
		this.length = length;
	}

	public boolean isLooping()
	{
		return looping;
	}

	public void setLooping(boolean looping)
	{
		this.looping = looping;
	}

	public boolean isDone()
	{
		return time == length;
	}
}
